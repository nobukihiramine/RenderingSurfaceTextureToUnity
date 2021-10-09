using System;
using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;

public class RawImageScript : MonoBehaviour
{
    // コンポーネント変数
    private RawImage m_rawimage;

    // メンバー変数
    private AndroidJavaObject m_ajoSurfaceTextureRenderer;
    private bool m_bPlaying;

    // Start is called before the first frame update
    void Start()
    {
        m_rawimage = GetComponent<RawImage>();

        // テクスチャーの作成
        // filterMode : 画像の補間方法を指定する。Point = 補完しない。他の選択肢は、Bilinear、Trilinear。 
        Texture2D texture2d = new Texture2D( 512, 512, TextureFormat.ARGB32, false, false );
        texture2d.filterMode = FilterMode.Point;

        // UnityのRawImageのテクスチャとして設定
        m_rawimage.texture = texture2d;
    }

    // Update is called once per frame
    void Update()
    {
        if( null != m_ajoSurfaceTextureRenderer )
        {
            // テクスチャ更新
            m_ajoSurfaceTextureRenderer.Call( "updateSurfaceTexture" );
        }
    }

    // イベント処理
    public void OnEvent()
    {
        if( null == m_ajoSurfaceTextureRenderer )
        {
            // 初回は、TextureRendererを初期化する。
            InitializeTextureRenderer( 512, 512 );
        }

        // Surfaceでの描画
        if( m_bPlaying )
        {
            m_ajoSurfaceTextureRenderer.Call( "stopDrawInSurface" );
            m_bPlaying = false;
        }
        else
        {
            m_ajoSurfaceTextureRenderer.Call( "startDrawInSurface" );
            m_bPlaying = true;
        }
    }

    // TextureRendererの初期化
    public void InitializeTextureRenderer( int iTextureWidth, int iTextureHeight )
    {
        Debug.Log( "InitializeTextureRenderer() start" );

        // テクスチャIDの取得とメンバ変数への設定
        IntPtr intptrTextureID = ((Texture2D)m_rawimage.texture).GetNativeTexturePtr();
        Debug.Log( "  intptrTextureID = " + intptrTextureID.ToString() );

        // SurfaceTextureRendererオブジェクトの生成
        m_ajoSurfaceTextureRenderer = new AndroidJavaObject( "com.hiramine.surfacetexturerenderer.SurfaceTextureRenderer",
            iTextureWidth,
            iTextureHeight,
            intptrTextureID.ToInt32() );    // Unityテクスチャを使用
        Debug.Log( "  m_ajoSurfaceTextureRenderer = " + m_ajoSurfaceTextureRenderer.ToString() );

        // Rendererの初期化
        m_ajoSurfaceTextureRenderer.Call( "init" );

        Debug.Log( "InitializeTextureRenderer() end" );
    }
}
