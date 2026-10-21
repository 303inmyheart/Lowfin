package de.lowfin.tv;

import android.content.Context;
import android.opengl.GLES20;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.*;
import androidx.media3.effect.*;
import java.io.IOException;

/** One five-sample SDR pass, one output texture, no temporal frame cache. */
@UnstableApi
final class DenoiseEffect implements GlEffect {
    @Override public BaseGlShaderProgram toGlShaderProgram(Context context,boolean useHdr) throws VideoFrameProcessingException { return new Shader(context,useHdr); }
    private static final class Shader extends BaseGlShaderProgram {
        private final GlProgram program;
        private final boolean hdr;
        Shader(Context context,boolean useHdr) throws VideoFrameProcessingException {
            super(useHdr,1);hdr=useHdr;
            if(useHdr) throw new VideoFrameProcessingException(new IllegalArgumentException("Noise reduction requires SDR"));
            try {
                program=new GlProgram(context,"shaders/lowfin_denoise_vertex.glsl","shaders/lowfin_denoise_fragment.glsl");
                program.setBufferAttribute("aPosition",GlUtil.getNormalizedCoordinateBounds(),GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
            } catch(IOException|GlUtil.GlException error) { throw new VideoFrameProcessingException(error); }
        }
        @Override public Size configure(int width,int height) throws VideoFrameProcessingException {
            if(width>1920||height>1080) throw new VideoFrameProcessingException(new IllegalArgumentException("Noise reduction requires at most 1080p"));
            program.setFloatsUniform("uStep",new float[]{1f/width,1f/height});
            // A runtime fence if the stream changes format after metadata negotiation.
            program.setFloatUniform("uStrength",!hdr&&width<=1920&&height<=1080?.65f:0f);
            return new Size(width,height);
        }
        @Override public void drawFrame(int texture,long timestamp) throws VideoFrameProcessingException {
            try {
                program.use();program.setSamplerTexIdUniform("uTexture",texture,0);program.bindAttributesAndUniforms();
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GlUtil.checkGlError();
            } catch(GlUtil.GlException error) { throw new VideoFrameProcessingException(error,timestamp); }
        }
        @Override public void release() throws VideoFrameProcessingException {
            try { super.release(); } finally { try { program.delete(); } catch(GlUtil.GlException error) { throw new VideoFrameProcessingException(error); } }
        }
    }
}
