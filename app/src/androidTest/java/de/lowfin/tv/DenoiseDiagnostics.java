package de.lowfin.tv;

import android.content.Context;
import android.opengl.*;
import java.nio.*;

/** Compile the shipped shader and verify real GPU pixels without changing playback/settings/cache. */
final class DenoiseDiagnostics {
    static String inspect(Context context) throws Exception {
        // Probe the actual optimized target classes, not debug/test copies. Same reflected
        // constructor/method/graph-factory path used by Media3 playback initialization.
        ClassLoader loader=context.getClassLoader();
        Class<?> builderType=loader.loadClass("androidx.media3.effect.DefaultVideoFrameProcessor$Factory$Builder");
        Object builder=builderType.getConstructor().newInstance();
        builderType.getMethod("setEnableReplayableCache",boolean.class).invoke(builder,false);
        Object factory=builderType.getMethod("build").invoke(builder);
        Class<?> graphType=loader.loadClass("androidx.media3.effect.SingleInputVideoGraph$Factory");
        Object graph=graphType.getConstructors()[0].newInstance(factory);
        if(graph==null) throw new IllegalStateException("Media3 graph factory missing");
        EGLDisplay display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);int[] version=new int[2];
        if(!EGL14.eglInitialize(display,version,0,version,1)) throw new IllegalStateException("EGL initialization failed");
        EGLContext eglContext=EGL14.EGL_NO_CONTEXT;EGLSurface surface=EGL14.EGL_NO_SURFACE;
        int program=0,vertex=0,fragment=0;int[] texture={0};
        try {
            EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
            if(!EGL14.eglChooseConfig(display,new int[]{EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_NONE},0,configs,0,1,count,0)||count[0]==0) throw new IllegalStateException("No RGBA EGL config");
            eglContext=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
            surface=EGL14.eglCreatePbufferSurface(display,configs[0],new int[]{EGL14.EGL_WIDTH,16,EGL14.EGL_HEIGHT,16,EGL14.EGL_NONE},0);
            if(!EGL14.eglMakeCurrent(display,surface,surface,eglContext)) throw new IllegalStateException("EGL context failed");
            vertex=shader(context,GLES20.GL_VERTEX_SHADER,"shaders/lowfin_denoise_vertex.glsl");fragment=shader(context,GLES20.GL_FRAGMENT_SHADER,"shaders/lowfin_denoise_fragment.glsl");
            program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);GLES20.glLinkProgram(program);int[] ok=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0) throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));GLES20.glUseProgram(program);
            FloatBuffer points=ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();points.put(new float[]{-1,-1,0,1,-1,1,0,1,1,-1,0,1,1,1,0,1}).position(0);int position=GLES20.glGetAttribLocation(program,"aPosition");GLES20.glEnableVertexAttribArray(position);GLES20.glVertexAttribPointer(position,4,GLES20.GL_FLOAT,false,0,points);
            ByteBuffer input=ByteBuffer.allocateDirect(16*16*4);
            for(int y=0;y<16;y++) for(int x=0;x<16;x++) {int value=(x<8?32:224)+((x+y)%2==0?-3:3);input.put((byte)value).put((byte)value).put((byte)value).put((byte)255);}input.position(0);
            GLES20.glGenTextures(1,texture,0);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_NEAREST);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,16,16,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,input);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uTexture"),0);GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"uStep"),1f/16,1f/16);GLES20.glViewport(0,0,16,16);
            ByteBuffer output=ByteBuffer.allocateDirect(16*16*4);int strength=GLES20.glGetUniformLocation(program,"uStrength");
            GLES20.glUniform1f(strength,0);draw(output);for(int i=0;i<input.capacity();i++) if(Math.abs((input.get(i)&255)-(output.get(i)&255))>1) throw new IllegalStateException("Passthrough differs at "+i);
            GLES20.glUniform1f(strength,.65f);draw(output);int quiet=output.get((4*16+4)*4)&255;if(quiet<=29||quiet>=35) throw new IllegalStateException("Noise not reduced: "+quiet);
            int edge=Math.abs((output.get((4*16+8)*4)&255)-(output.get((4*16+7)*4)&255));if(edge<180) throw new IllegalStateException("Edge blurred: "+edge);
            for(int i=3;i<output.capacity();i+=4) if((output.get(i)&255)!=255) throw new IllegalStateException("Alpha changed");
            return "Optimized Media3 reflection/factory initialization passed; GPU shader compiled; passthrough unchanged; noise reduced; edge preserved; alpha unchanged. Renderer: "+GLES20.glGetString(GLES20.GL_RENDERER);
        } finally {
            if(program!=0) GLES20.glDeleteProgram(program);if(vertex!=0) GLES20.glDeleteShader(vertex);if(fragment!=0) GLES20.glDeleteShader(fragment);if(texture[0]!=0) GLES20.glDeleteTextures(1,texture,0);
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);if(surface!=EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display,surface);if(eglContext!=EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display,eglContext);EGL14.eglTerminate(display);
        }
    }
    private static int shader(Context context,int type,String asset) throws Exception {
        String source;try(java.io.InputStream input=context.getAssets().open(asset)) {java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1) bytes.write(buffer,0,n);source=bytes.toString("UTF-8");}
        int shader=GLES20.glCreateShader(type);GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader);int[] ok=new int[1];GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0) {String error=GLES20.glGetShaderInfoLog(shader);GLES20.glDeleteShader(shader);throw new IllegalStateException(error);}return shader;
    }
    private static void draw(ByteBuffer output) {
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glFinish();output.position(0);GLES20.glReadPixels(0,0,16,16,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,output);if(GLES20.glGetError()!=GLES20.GL_NO_ERROR) throw new IllegalStateException("OpenGL draw failed");
    }
}
