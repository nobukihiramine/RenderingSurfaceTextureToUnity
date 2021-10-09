package com.hiramine.surfacetexturerenderer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

class SurfaceTextureRenderer
{
	// 定数
	private static final String LOGTAG       = "SurfaceTextureRenderer";
	private static final int    SIZEOF_FLOAT = Float.SIZE / 8;    // Float.SIZEで、float型のビット数が得られるので、8で割って、バイト数を得る

	// シェーダーコード
	private final static String VERTEX_SHADER_CODE   =
			"uniform mat4 uniformSurfaceTextureMatrix;\n" +
			"attribute vec4 attributePosition;\n" +
			"attribute vec4 attributeTexCoord;\n" +
			"varying vec2 varyingTexCoord;\n" +
			"void main() {\n" +
			"  gl_Position = attributePosition;\n" +
			"  varyingTexCoord = (uniformSurfaceTextureMatrix * attributeTexCoord).xy;\n" +
			"}\n";
	// 1行目：GL_TEXTURE_EXTERNAL_OESテクスチャを使用するための宣言。
	// 4行目：sampler2D変数の代わりにsamplerExternalOES変数を定義。
	private final static String FRAGMENT_SHADER_CODE =
			"#extension GL_OES_EGL_image_external : require\n" +
			"precision mediump float;\n" +
			"varying vec2 varyingTexCoord;\n" +
			"uniform samplerExternalOES uniformTexture;\n" +
			"void main() {\n" +
			"  gl_FragColor = texture2D(uniformTexture, varyingTexCoord);\n" +
			"}\n";

	// GL関連
	private final int m_iTextureWidth;
	private final int m_iTextureHeight;
	private final int m_iTextureID_unity;
	private       int m_iTextureID_android;

	// シェーダープログラム関連
	private int m_iShaderProgram;
	private int m_iPositionAttributeLocation;
	private int m_iTexCoordAttributeLocation;
	private int m_iSTMatrixUniformLocation;

	// バッファオブジェクト
	private int m_iFboID_unitytexture;
	private int m_iVboID_rectangle;

	// テクスチャ描画関連
	private final float[] m_f16STMatrix     = new float[16];
	private final int[]   m_ai4Viewport_old = new int[4];

	// Surface関連
	private Surface        m_surface;
	private SurfaceTexture m_surfacetexture;
	private boolean        m_bNewFrameAvailable;

	// Surfaceでの描画関連
	private final Paint    m_paint                 = new Paint();
	private final Random   m_random                = new Random();
	private final Handler  m_handlerDrawInSurface  = new Handler( Looper.getMainLooper() );
	private final Runnable m_runnableDrawInSurface = new Runnable()
	{
		@Override
		public void run()
		{
			drawInSurface();
			m_handlerDrawInSurface.postDelayed( m_runnableDrawInSurface, 100 );
		}
	};

	// イベントリスナー変数
	private final SurfaceTexture.OnFrameAvailableListener m_onFrameAvailableListener = new SurfaceTexture.OnFrameAvailableListener()
	{
		@Override
		public void onFrameAvailable( SurfaceTexture surfaceTexture )
		{
			Log.d( LOGTAG, "onFrameAvailable() start" );
			Log.d( LOGTAG, "  Thread name = " + Thread.currentThread().getName() );    // Thread name = UnityMain

			synchronized( this )
			{
				m_bNewFrameAvailable = true;
			}

			Log.d( LOGTAG, "onFrameAvailable() end" );
		}
	};

	// コンストラクタ
	public SurfaceTextureRenderer( int iTextureWidth,
								   int iTextureHeight,
								   int iTextureID_unity )
	{
		Log.d( LOGTAG, "Constructor start" );
		Log.d( LOGTAG, "  Thread name = " + Thread.currentThread().getName() );    // Thread name = UnityMain

		m_iTextureWidth = iTextureWidth;
		m_iTextureHeight = iTextureHeight;
		m_iTextureID_unity = iTextureID_unity;

		Log.d( LOGTAG, "  m_iTextureWidth  = " + m_iTextureWidth );
		Log.d( LOGTAG, "  m_iTextureHeight = " + m_iTextureHeight );
		Log.d( LOGTAG, "  m_iTextureID_unity = " + m_iTextureID_unity );

		Log.d( LOGTAG, "Constructor end" );
	}

	// Surfaceの初期化
	private void initSurface()
	{
		Log.d( LOGTAG, "initSurface() start" );
		Log.d( LOGTAG, "  Thread name = " + Thread.currentThread().getName() );    // Thread name = UnityMain

		checkGlError( "initSurface() start" );

		// テクスチャの生成
		int[] aiTextureID = new int[1];
		GLES20.glGenTextures( 1, aiTextureID, 0 );
		checkGlError( "glGenTextures()" );
		m_iTextureID_android = aiTextureID[0];

		// アクティブにするテクスチャユニットの指定
		GLES20.glActiveTexture( GLES20.GL_TEXTURE0 );
		checkGlError( "glActiveTexture( GL_TEXTURE0 )" );

		// テクスチャのバインド
		GLES20.glBindTexture( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, m_iTextureID_android );
		checkGlError( "glBindTexture( m_iTextureID_android )" );

		// テクスチャパラメータの設定
		GLES20.glTexParameteri( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR );
		GLES20.glTexParameteri( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR );
		GLES20.glTexParameteri( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE );
		GLES20.glTexParameteri( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE );
		checkGlError( "glTexParameterf()" );

		// テクスチャのバインドの解除
		GLES20.glBindTexture( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0 );
		checkGlError( "glBindTexture( 0 )" );

		// SurfaceTextureとSurfaceの作成
		m_surfacetexture = new SurfaceTexture( m_iTextureID_android );
		m_surfacetexture.setOnFrameAvailableListener( m_onFrameAvailableListener );
		m_surfacetexture.setDefaultBufferSize( m_iTextureWidth, m_iTextureHeight );
		m_surface = new Surface( m_surfacetexture );

		synchronized( this )
		{
			m_bNewFrameAvailable = false;
		}

		checkGlError( "initSurface() end" );

		Log.d( LOGTAG, "initSurface() end" );
	}

	// SurfaceTextureの更新
	public void updateSurfaceTexture()
	{
		Log.d( LOGTAG, "updateSurfaceTexture() start" );
		Log.d( LOGTAG, "  Thread name = " + Thread.currentThread().getName() );    // Thread name = UnityMain

		synchronized( this )
		{
			Log.d( LOGTAG, "  m_bNewFrameAvailable = " + m_bNewFrameAvailable );
			if( m_bNewFrameAvailable )
			{
				m_bNewFrameAvailable = false;

				if( !Thread.currentThread().getName().equals( "UnityMain" ) )
				{
					Log.e( LOGTAG, "  Not called from render thread and hence update texture will fail" );
				}

				Log.d( LOGTAG, "  updateTexImage() start" );
				checkGlError( "updateTexImage() start" );
				m_surfacetexture.updateTexImage();
				checkGlError( "updateTexImage() end" );
				Log.d( LOGTAG, "  updateTexImage() end" );

				// SurfaceTexture変換行列の取得
				m_surfacetexture.getTransformMatrix( m_f16STMatrix );
				checkGlError( "getTransformMatrix()" );

				// Androidテクスチャをフレームバッファオブジェクトに描画
				renderAndroidTexture2FBO();
			}
		}

		Log.d( LOGTAG, "updateSurfaceTexture() end" );
	}

	// Surfaceでの描画
	public void drawInSurface()
	{
		Log.d( LOGTAG, "drawInSurface() start" );
		Log.d( LOGTAG, "  Thread name = " + Thread.currentThread().getName() );    // Thread name = UnityMain

		try
		{
			Canvas canvas = m_surface.lockCanvas( null );

			// 全体塗りつぶし
			canvas.drawColor( Color.rgb( 0, 0, 128 ) );

			// 矩形
			m_paint.setColor( Color.rgb( m_random.nextInt( 255 ), m_random.nextInt( 255 ), m_random.nextInt( 255 ) ) );
			m_paint.setStyle( Paint.Style.FILL );
			int iX          = m_random.nextInt( m_iTextureWidth );
			int iY          = m_random.nextInt( m_iTextureWidth );
			int iHalfWidth  = m_random.nextInt( 200 );
			int iHalfHeight = m_random.nextInt( 200 );
			canvas.drawRect( iX - iHalfWidth, iY - iHalfHeight, iX + iHalfWidth, iY + iHalfHeight, m_paint );

			// 円
			m_paint.setColor( Color.rgb( m_random.nextInt( 255 ), m_random.nextInt( 255 ), m_random.nextInt( 255 ) ) );
			m_paint.setStyle( Paint.Style.STROKE );
			m_paint.setStrokeWidth( 30 );
			int iRadius = m_random.nextInt( 200 );
			canvas.drawCircle( m_random.nextInt( m_iTextureWidth ), m_random.nextInt( m_iTextureHeight ), iRadius, m_paint );

			m_surface.unlockCanvasAndPost( canvas );
		}
		catch( Surface.OutOfResourcesException e )
		{
			Log.e( LOGTAG, "  drawInSurface() : failed" );
			e.printStackTrace();
		}

		Log.d( LOGTAG, "drawInSurface() end" );
	}

	// GLESエラーチェック
	private static void checkGlError( String str )
	{
		while( true )
		{
			int iError = GLES20.glGetError();
			if( GLES20.GL_NO_ERROR == iError )
			{
				break;
			}

			Log.e( "checkGlError", str + " : Error Code = " + iError );
		}
	}

	// 初期化処理まとめ
	public void init()
	{
		// Surfaceの初期化
		initSurface();

		// シェーダープログラムの初期化
		initShaderProgram();

		// フレームバッファオブジェクトの初期化
		initFBO();

		// 頂点バッファオブジェクトの初期化
		initVBO();

		// クリア色の指定
		GLES20.glClearColor( 0.0f, 0.0f, 0.0f, 1.0f );
		checkGlError( "glClearColor()" );
	}

	// シェーダープログラムの初期化
	private void initShaderProgram()
	{
		// シェーダープログラムの作成
		m_iShaderProgram = createShaderProgram( VERTEX_SHADER_CODE, FRAGMENT_SHADER_CODE );
		if( 0 == m_iShaderProgram )
		{
			return;
		}

		// Variable変数の場所の取得
		m_iPositionAttributeLocation = GLES20.glGetAttribLocation( m_iShaderProgram, "attributePosition" );
		checkGlError( "glGetAttribLocation( attributePosition )" );
		m_iTexCoordAttributeLocation = GLES20.glGetAttribLocation( m_iShaderProgram, "attributeTexCoord" );
		checkGlError( "glGetAttribLocation( attributeTexCoord )" );

		// Uniform変数の場所の取得
		m_iSTMatrixUniformLocation = GLES20.glGetUniformLocation( m_iShaderProgram, "uniformSurfaceTextureMatrix" );
		checkGlError( "glGetUniformLocation( uniformSurfaceTextureMatrix )" );
	}

	// シェーダープログラムの作成
	private static int createShaderProgram( String strVertexShaderCode, String strFragmentShaderCode )
	{
		// シェーダーの読み込み
		int iVertexShader = loadShader( GLES20.GL_VERTEX_SHADER, strVertexShaderCode );
		if( 0 == iVertexShader )
		{
			Log.e( LOGTAG, "Load shader : failed." );
			return 0;
		}
		// シェーダーの読み込み
		int iFragmentShader = loadShader( GLES20.GL_FRAGMENT_SHADER, strFragmentShaderCode );
		if( 0 == iFragmentShader )
		{
			Log.e( LOGTAG, "Load shader : failed." );
			return 0;
		}

		// シェーダープログラムの作成
		int iProgram = GLES20.glCreateProgram();
		checkGlError( "glCreateProgram()" );
		if( 0 == iProgram )
		{
			Log.e( LOGTAG, "Create program : failed." );
			return 0;
		}

		// シェーダープログラムにシェーダーを割り付け
		GLES20.glAttachShader( iProgram, iVertexShader );
		checkGlError( "glAttachShader( VertexShader )" );
		GLES20.glAttachShader( iProgram, iFragmentShader );
		checkGlError( "glAttachShader( FragmentShader )" );

		// シェーダープログラムのリンク
		GLES20.glLinkProgram( iProgram );
		checkGlError( "glLinkProgram()" );

		// リンク結果の確認
		int[] aiLinkStatus = new int[1];
		GLES20.glGetProgramiv( iProgram, GLES20.GL_LINK_STATUS, aiLinkStatus, 0 );
		if( GLES20.GL_FALSE == aiLinkStatus[0] )
		{
			Log.e( LOGTAG, "Link program : failed." );
			Log.e( LOGTAG, "  " + GLES20.glGetProgramInfoLog( iProgram ) );
			GLES20.glDeleteProgram( iProgram );
			return 0;
		}

		return iProgram;
	}

	// シェーダーの読み込み
	private static int loadShader( int iShaderType, String strShaderCode )
	{
		// シェーダーの作成
		int iShader = GLES20.glCreateShader( iShaderType );
		checkGlError( "glCreateShader()" );
		if( 0 == iShader )
		{
			Log.e( LOGTAG, "Create shader : failed. Shader Type = " + iShader );
			Log.e( LOGTAG, "( GLES20.GL_VERTEX_SHADER = " + GLES20.GL_VERTEX_SHADER + " )" );
			Log.e( LOGTAG, "( GLES20.GL_FRAGMENT_SHADER = " + GLES20.GL_FRAGMENT_SHADER + " )" );
			return 0;
		}

		// シェーダーにシェーダーコードをセットし、コンパイル。
		GLES20.glShaderSource( iShader, strShaderCode );
		checkGlError( "glShaderSource()" );
		GLES20.glCompileShader( iShader );
		checkGlError( "glCompileShader()" );

		// コンパイル結果の確認
		int[] aiCompiled = new int[1];
		GLES20.glGetShaderiv( iShader, GLES20.GL_COMPILE_STATUS, aiCompiled, 0 );
		if( GLES20.GL_FALSE == aiCompiled[0] )
		{
			Log.e( LOGTAG, "Compile shader : failed. Shader Type = " + iShader );
			Log.e( LOGTAG, "( GLES20.GL_VERTEX_SHADER = " + GLES20.GL_VERTEX_SHADER + " )" );
			Log.e( LOGTAG, "( GLES20.GL_FRAGMENT_SHADER = " + GLES20.GL_FRAGMENT_SHADER + " )" );
			Log.e( LOGTAG, "  ShaderInfoLog : " + GLES20.glGetShaderInfoLog( iShader ) );
			GLES20.glDeleteShader( iShader );
			return 0;
		}

		return iShader;
	}

	// フレームバッファオブジェクトの初期化
	private void initFBO()
	{
		// メモ）
		// フレームバッファオブジェクトを作成し、作成したフレームバッファオブジェクトにカラーバッファとしてUnityのテクスチャをアタッチする。
		// これにより「作成したフレームバッファオブジェクト」の色に関するデータ領域は「Unityテクスチャ」のデータ領域となる。
		// これにより「フレームバッファオブジェクトに対して描画」すると「Unityテクスチャに対して描画」したことになる。

		// フレームバッファオブジェクトの生成
		int[] aiFboID = new int[1];
		GLES20.glGenFramebuffers( 1, aiFboID, 0 );
		checkGlError( "glGenFramebuffers()" );
		m_iFboID_unitytexture = aiFboID[0];
		Log.d( LOGTAG, "  m_iFBO_unity = " + m_iFboID_unitytexture );
		// Bind Frame Buffer
		GLES20.glBindFramebuffer( GLES20.GL_FRAMEBUFFER, m_iFboID_unitytexture );
		checkGlError( "glBindFramebuffer( m_iFBO_unity )" );
		// フレームバッファオブジェクトにカラーバッファとしてテクスチャをアタッチ
		GLES20.glFramebufferTexture2D( GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, m_iTextureID_unity, 0 );
		checkGlError( "glFramebufferTexture2D()" );
		// Unbind Frame Buffer
		GLES20.glBindFramebuffer( GLES20.GL_FRAMEBUFFER, 0 );
		checkGlError( "glBindFramebuffer( 0 )" );
	}

	// 頂点バッファオブジェクトの構築
	private void initVBO()
	{
		// テクスチャを貼り付ける矩形４頂点の位置座標値(X,Y,Z)とテクスチャ座標値(U,V)の初期化
		float[] afVertex = { // X, Y, Z, U, V
							 -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,
							 1.0f, -1.0f, 0.0f, 1.0f, 0.0f,
							 -1.0f, 1.0f, 0.0f, 0.0f, 1.0f,
							 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, };
		FloatBuffer fbVertex = makeFloatBuffer( afVertex );

		// VBOの生成
		int[] aiVboID = new int[1];
		GLES20.glGenBuffers( 1, aiVboID, 0 );
		checkGlError( "glGenBuffers()" );
		m_iVboID_rectangle = aiVboID[0];

		// VBOに座標値データの転送
		GLES20.glBindBuffer( GLES20.GL_ARRAY_BUFFER, m_iVboID_rectangle );
		checkGlError( "glBindBuffer( VBO )" );
		GLES20.glBufferData( GLES20.GL_ARRAY_BUFFER, fbVertex.capacity() * SIZEOF_FLOAT, fbVertex, GLES20.GL_STATIC_DRAW );
		checkGlError( "glBufferData()" );
		GLES20.glBindBuffer( GLES20.GL_ARRAY_BUFFER, 0 );
		checkGlError( "glBindBuffer( 0 )" );
	}

	// floatバッファの作成
	private static FloatBuffer makeFloatBuffer( float[] arr )
	{
		ByteBuffer bb = ByteBuffer.allocateDirect( arr.length * SIZEOF_FLOAT );
		bb.order( ByteOrder.nativeOrder() );
		FloatBuffer fb = bb.asFloatBuffer();
		fb.put( arr );
		fb.position( 0 );
		return fb;
	}

	// Androidテクスチャをフレームバッファオブジェクトに描画
	private void renderAndroidTexture2FBO()
	{
		// Viewport
		GLES20.glGetIntegerv( GLES20.GL_VIEWPORT, m_ai4Viewport_old, 0 );
		checkGlError( "glGetIntegerv( GL_VIEWPORT )" );
		GLES20.glViewport( 0, 0, m_iTextureWidth, m_iTextureHeight );
		checkGlError( "glViewport()" );

		// 使用するシェーダープログラムの指定
		GLES20.glUseProgram( m_iShaderProgram );
		checkGlError( "glUseProgram()" );

		// アクティブにするテクスチャユニットの指定
		GLES20.glActiveTexture( GLES20.GL_TEXTURE0 );
		checkGlError( "glActiveTexture( GL_TEXTURE0 )" );

		// テクスチャのバインド
		GLES20.glBindTexture( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, m_iTextureID_android );
		checkGlError( "glBindTexture( m_iTextureID_external )" );

		// VBOのバインド
		GLES20.glBindBuffer( GLES20.GL_ARRAY_BUFFER, m_iVboID_rectangle );
		checkGlError( "glBindBuffer( VBO )" );
		// シェーダープログラムへ頂点座標値データの転送＆有効化
		GLES20.glVertexAttribPointer( m_iPositionAttributeLocation, 3, GLES20.GL_FLOAT, false, 5 * SIZEOF_FLOAT,
									  0 );
		checkGlError( "glVertexAttribPointer( Position )" );
		GLES20.glEnableVertexAttribArray( m_iPositionAttributeLocation );
		checkGlError( "glEnableVertexAttribArray( Position )" );
		// シェーダープログラムへテクスチャ座標値データの転送＆有効化
		GLES20.glVertexAttribPointer( m_iTexCoordAttributeLocation, 2, GLES20.GL_FLOAT, false, 5 * SIZEOF_FLOAT,
									  3 * SIZEOF_FLOAT );
		checkGlError( "glVertexAttribPointer( TexCoord )" );
		GLES20.glEnableVertexAttribArray( m_iTexCoordAttributeLocation );
		checkGlError( "glEnableVertexAttribArray( TexCoord )" );
		// VBOのバインドの解除
		GLES20.glBindBuffer( GLES20.GL_ARRAY_BUFFER, 0 );
		checkGlError( "glBindBuffer( 0 )" );

		// シェーダープログラムへ行列データの転送
		GLES20.glUniformMatrix4fv( m_iSTMatrixUniformLocation, 1, false, m_f16STMatrix, 0 );
		checkGlError( "glUniformMatrix4fv( STMatrix )" );

		// FBOのバインド
		GLES20.glBindFramebuffer( GLES20.GL_FRAMEBUFFER, m_iFboID_unitytexture );
		checkGlError( "glBindFramebuffer()" );

		// 背景クリア
		GLES20.glClear( GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT );
		checkGlError( "glClear()" );

		// テクスチャ矩形描画
		GLES20.glDrawArrays( GLES20.GL_TRIANGLE_STRIP, 0, 4 );
		checkGlError( "glDrawArrays()" );

		// GLフラッシュ
		GLES20.glFlush();
		checkGlError( "glFlush()" );

		// FBOのバインドの解除
		GLES20.glBindFramebuffer( GLES20.GL_FRAMEBUFFER, 0 );
		checkGlError( "glBindFramebuffer( 0 )" );

		// テクスチャのバインドの解除
		GLES20.glBindTexture( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0 );
		checkGlError( "glBindTexture( 0 )" );

		// Viewportの復帰
		GLES20.glViewport( m_ai4Viewport_old[0], m_ai4Viewport_old[1], m_ai4Viewport_old[2], m_ai4Viewport_old[3] );
		checkGlError( "glViewport( old )" );
	}

	// Surfaceでの連続描画の開始
	public void startDrawInSurface()
	{
		m_handlerDrawInSurface.postDelayed( m_runnableDrawInSurface, 0 );
	}

	// Surfaceでの連続描画の停止
	public void stopDrawInSurface()
	{
		m_handlerDrawInSurface.removeCallbacks( m_runnableDrawInSurface );
	}
}
