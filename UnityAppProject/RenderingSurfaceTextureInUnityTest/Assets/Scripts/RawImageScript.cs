using System;
using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;

public class RawImageScript : MonoBehaviour
{
    // �R���|�[�l���g�ϐ�
    private RawImage m_rawimage;

    // �����o�[�ϐ�
    private AndroidJavaObject m_ajoSurfaceTextureRenderer;
    private bool m_bPlaying;

    // Start is called before the first frame update
    void Start()
    {
        m_rawimage = GetComponent<RawImage>();

        // �e�N�X�`���[�̍쐬
        // filterMode : �摜�̕�ԕ��@���w�肷��BPoint = �⊮���Ȃ��B���̑I�����́ABilinear�ATrilinear�B 
        Texture2D texture2d = new Texture2D( 512, 512, TextureFormat.ARGB32, false, false );
        texture2d.filterMode = FilterMode.Point;

        // Unity��RawImage�̃e�N�X�`���Ƃ��Đݒ�
        m_rawimage.texture = texture2d;
    }

    // Update is called once per frame
    void Update()
    {
        if( null != m_ajoSurfaceTextureRenderer )
        {
            // �e�N�X�`���X�V
            m_ajoSurfaceTextureRenderer.Call( "updateSurfaceTexture" );
        }
    }

    // �C�x���g����
    public void OnEvent()
    {
        if( null == m_ajoSurfaceTextureRenderer )
        {
            // ����́ATextureRenderer������������B
            InitializeTextureRenderer( 512, 512 );
        }

        // Surface�ł̕`��
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

    // TextureRenderer�̏�����
    public void InitializeTextureRenderer( int iTextureWidth, int iTextureHeight )
    {
        Debug.Log( "InitializeTextureRenderer() start" );

        // �e�N�X�`��ID�̎擾�ƃ����o�ϐ��ւ̐ݒ�
        IntPtr intptrTextureID = ((Texture2D)m_rawimage.texture).GetNativeTexturePtr();
        Debug.Log( "  intptrTextureID = " + intptrTextureID.ToString() );

        // SurfaceTextureRenderer�I�u�W�F�N�g�̐���
        m_ajoSurfaceTextureRenderer = new AndroidJavaObject( "com.hiramine.surfacetexturerenderer.SurfaceTextureRenderer",
            iTextureWidth,
            iTextureHeight,
            intptrTextureID.ToInt32() );    // Unity�e�N�X�`�����g�p
        Debug.Log( "  m_ajoSurfaceTextureRenderer = " + m_ajoSurfaceTextureRenderer.ToString() );

        // Renderer�̏�����
        m_ajoSurfaceTextureRenderer.Call( "init" );

        Debug.Log( "InitializeTextureRenderer() end" );
    }
}
