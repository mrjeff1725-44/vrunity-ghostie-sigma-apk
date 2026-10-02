package com.vrunity.vrapk

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix

// The game on the GPU: the scene's geometry plus the shader that draws it. Both
// the headset's VR session and the phone's own screen mode draw through this, so
// the two always show the same game.
class Game(context: Context) {
    private val scene = Scene.load(context)
    private val shapes = HashMap<String, Mesh>()
    private val sky = SkyRenderer()
    private var program = 0
    private var aPos = 0
    private var aNormal = 0
    private var uMvp = 0
    private var uColor = 0
    private var uRot = 0
    private var uAlpha = 0
    private var uSunDir0 = 0
    private var uSunColor0 = 0
    private var uSunDir1 = 0
    private var uSunColor1 = 0
    private var uAmbSky = 0
    private var uAmbGround = 0
    private var uFogColor = 0
    private var uFogRange = 0
    private var uFogOn = 0
    private val mvp = FloatArray(16)
    private val scratch = FloatArray(16)

    val startX = scene.startX
    val startZ = scene.startZ
    val startYaw = scene.startYaw
    val eyeHeight = scene.eyeHeight

    // Safe to call again after the GPU context is recreated.
    fun setup() {
        if (program != 0) return
        shapes["cube"] = Mesh(Mesh.box())
        shapes["sphere"] = Mesh(Mesh.sphere())
        shapes["cylinder"] = Mesh(Mesh.cylinder())
        shapes["cone"] = Mesh(Mesh.cone())
        shapes["plane"] = Mesh(Mesh.plane())
        buildProgram()
        sky.setup(scene)
        // The scene's lights and fog are the same for every object, so they are set
        // once here instead of on every draw.
        GLES20.glUseProgram(program)
        GLES20.glUniform3f(uSunDir0, scene.sunDir[0], scene.sunDir[1], scene.sunDir[2])
        GLES20.glUniform3f(uSunColor0, scene.sunColor[0], scene.sunColor[1], scene.sunColor[2])
        GLES20.glUniform3f(uSunDir1, scene.sunDir[3], scene.sunDir[4], scene.sunDir[5])
        GLES20.glUniform3f(uSunColor1, scene.sunColor[3], scene.sunColor[4], scene.sunColor[5])
        GLES20.glUniform3f(uAmbSky, scene.ambientSky[0], scene.ambientSky[1], scene.ambientSky[2])
        GLES20.glUniform3f(uAmbGround, scene.ambientGround[0], scene.ambientGround[1], scene.ambientGround[2])
        GLES20.glUniform3f(uFogColor, scene.fogColor[0], scene.fogColor[1], scene.fogColor[2])
        GLES20.glUniform2f(uFogRange, scene.fogNear, scene.fogFar)
        GLES20.glUniform1f(uFogOn, scene.fogOn)
    }

    private fun shader(type: Int, source: String): Int {
        val id = GLES20.glCreateShader(type)
        GLES20.glShaderSource(id, source)
        GLES20.glCompileShader(id)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(id)
            GLES20.glDeleteShader(id)
            throw RuntimeException("Shader failed: " + log)
        }
        return id
    }

    private fun buildProgram() {
        val vertex = "uniform mat4 uMvp;attribute vec3 aPos;attribute vec3 aNormal;varying vec3 vN;varying float vDepth;" +
            "void main(){vN=aNormal;vec4 cp=uMvp*vec4(aPos,1.0);vDepth=cp.w;gl_Position=cp;}"
        // Lit the way the editor lights it: the scene's own lights brought into this
        // object's frame, the scene's fill light, the same filmic curve the editor
        // renders through, and the scene's fog on top.
        val fragment = "precision mediump float;varying vec3 vN;varying float vDepth;" +
            "uniform vec3 uColor;uniform mat3 uRot;uniform float uAlpha;" +
            "uniform vec3 uSunDir0;uniform vec3 uSunColor0;uniform vec3 uSunDir1;uniform vec3 uSunColor1;" +
            "uniform vec3 uAmbSky;uniform vec3 uAmbGround;uniform vec3 uFogColor;uniform vec2 uFogRange;uniform float uFogOn;" +
            "vec3 aces(vec3 c){c*=1.1/0.6;" +
            "mat3 inM=mat3(vec3(0.59719,0.07600,0.02840),vec3(0.35458,0.90834,0.13383),vec3(0.04823,0.01566,0.83777));" +
            "mat3 outM=mat3(vec3(1.60475,-0.10208,-0.00327),vec3(-0.53108,1.10813,-0.07276),vec3(-0.07367,-0.00605,1.07602));" +
            "vec3 v=inM*c;vec3 num=v*(v+0.0245786)-0.000090537;vec3 den=v*(0.983729*v+0.4329510)+0.238081;" +
            "return clamp(outM*(num/den),0.0,1.0);}" +
            "void main(){vec3 n=normalize(vN);" +
            "vec3 l0=normalize(uSunDir0*uRot);vec3 l1=normalize(uSunDir1*uRot);" +
            "vec3 irr=uSunColor0*max(dot(n,l0),0.0)+uSunColor1*max(dot(n,l1),0.0);" +
            "irr+=mix(uAmbGround,uAmbSky,n.y*0.5+0.5);" +
            "vec3 c=aces(uColor*irr*0.31830989);" +
            "float f=uFogOn*clamp((vDepth-uFogRange.x)/max(uFogRange.y-uFogRange.x,0.001),0.0,1.0);" +
            "gl_FragColor=vec4(pow(mix(c,uFogColor,f),vec3(0.4545)),uAlpha);}"
        val vs = shader(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = shader(GLES20.GL_FRAGMENT_SHADER, fragment)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("Program failed: " + GLES20.glGetProgramInfoLog(program))
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aNormal = GLES20.glGetAttribLocation(program, "aNormal")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uColor = GLES20.glGetUniformLocation(program, "uColor")
        uRot = GLES20.glGetUniformLocation(program, "uRot")
        uAlpha = GLES20.glGetUniformLocation(program, "uAlpha")
        uSunDir0 = GLES20.glGetUniformLocation(program, "uSunDir0")
        uSunColor0 = GLES20.glGetUniformLocation(program, "uSunColor0")
        uSunDir1 = GLES20.glGetUniformLocation(program, "uSunDir1")
        uSunColor1 = GLES20.glGetUniformLocation(program, "uSunColor1")
        uAmbSky = GLES20.glGetUniformLocation(program, "uAmbSky")
        uAmbGround = GLES20.glGetUniformLocation(program, "uAmbGround")
        uFogColor = GLES20.glGetUniformLocation(program, "uFogColor")
        uFogRange = GLES20.glGetUniformLocation(program, "uFogRange")
        uFogOn = GLES20.glGetUniformLocation(program, "uFogOn")
    }

    fun clear() {
        GLES20.glClearColor(scene.bg[0], scene.bg[1], scene.bg[2], 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
    }

    // A flat colour for the eye images — used by the startup check that the picture
    // really is reaching the lenses.
    fun clearTo(r: Float, g: Float, b: Float) {
        GLES20.glClearColor(r, g, b, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
    }

    // The sky behind everything: the same gradient, sun and stars the editor shows.
    fun drawSky(view: FloatArray, proj: FloatArray, camX: Float, camY: Float, camZ: Float) {
        sky.draw(view, proj, camX, camY, camZ, seconds())
    }

    // Draws the whole scene for one eye, given that eye's view and projection. Solid
    // objects are drawn first, then the see-through ones, so a scene that uses
    // opacity reads the same as it does in the editor.
    fun draw(view: FloatArray, proj: FloatArray) {
        GLES20.glUseProgram(program)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        drawItems(view, proj, false)
        drawItems(view, proj, true)
    }

    private fun drawItems(view: FloatArray, proj: FloatArray, transparent: Boolean) {
        if (transparent) {
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            GLES20.glDepthMask(false)
        } else {
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glDepthMask(true)
        }
        for (i in scene.items.indices) {
            val item = scene.items[i]
            if ((item.opacity < 1f) != transparent) continue
            Matrix.multiplyMM(scratch, 0, view, 0, item.model, 0)
            Matrix.multiplyMM(mvp, 0, proj, 0, scratch, 0)
            GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
            GLES20.glUniformMatrix3fv(uRot, 1, false, item.rotM, 0)
            GLES20.glUniform1f(uAlpha, item.opacity)
            GLES20.glUniform3f(uColor, item.color[0], item.color[1], item.color[2])
            val mesh = shapes[item.shape]
            if (mesh != null) mesh.draw(aPos, aNormal)
        }
        if (transparent) {
            GLES20.glDepthMask(true)
            GLES20.glDisable(GLES20.GL_BLEND)
        }
    }

    // Seconds since the app started, wrapped to an hour so the star twinkle keeps
    // working without the shader losing precision.
    private fun seconds(): Float = ((System.nanoTime() / 1000000L) % 3600000L) / 1000f
}
