package com.vrunity.vrapk

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix

// The game on the GPU: the scene's geometry plus the shader that draws it. Both
// the headset's VR session and the phone's own screen mode draw through this, so
// the two always show the same game.
class Game(context: Context) {
    private val context = context
    private val scene = Scene.load(context)
    private val shapes = HashMap<String, Mesh>()
    // The models and images the scene came with, loaded once at start-up.
    private val models = HashMap<String, BakedModel>()
    private val textures = HashMap<String, Int>()
    private val sky = SkyRenderer()
    // Bound wherever a surface has no image of its own, so the shader always has
    // something to sample.
    private var white = 0
    private var program = 0
    private var aPos = 0
    private var aNormal = 0
    private var aUv = 0
    private var uMvp = 0
    private var uColor = 0
    private var uRot = 0
    private var uAlpha = 0
    private var uTex = 0
    private var uUseTex = 0
    private var uUvRepeat = 0
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
    // The last moment the scene was stepped, so its animation moves evenly however
    // quickly the headset draws.
    private var lastFrame = -1f

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
        white = BakedModel.white()
        // Every model and image the scene was built with is read from the app's own
        // files. A file that is missing leaves that object out rather than stopping
        // the game.
        for (item in scene.items) {
            if (item.model.isEmpty() || models.containsKey(item.model)) continue
            val loaded = BakedModel.load(context, item.model, textures)
            if (loaded != null) models[item.model] = loaded
            else android.util.Log.e("VRUnityXR", "Missing game file: " + item.model)
        }
        for (item in scene.items) {
            loadTexture(item.tex)
        }
        for (model in models.values) {
            for (part in model.parts) loadTexture(part.image)
        }
        sky.setup(scene)
        // The scene's own sounds, read from the app's own files.
        Audio.start(context, scene.sounds)
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

    // An image a surface uses, decoded once and shared.
    private fun loadTexture(file: String) {
        if (file.isEmpty() || textures.containsKey(file)) return
        BakedModel.texture(context, file, textures)
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
        val vertex = "uniform mat4 uMvp;attribute vec3 aPos;attribute vec3 aNormal;attribute vec2 aUv;" +
            "varying vec3 vN;varying vec2 vUv;varying float vDepth;" +
            "void main(){vN=aNormal;vUv=aUv;vec4 cp=uMvp*vec4(aPos,1.0);vDepth=cp.w;gl_Position=cp;}"
        // Lit the way the editor lights it: the scene's own lights brought into this
        // object's frame, the scene's fill light, the same filmic curve the editor
        // renders through, and the scene's fog on top.
        // Texture coordinates arrive with the image's top row first, so the picture
        // is sampled as it is. An image is sRGB, and the lighting here is linear, so
        // it is brought into the same space the editor renders it in.
        val fragment = "precision mediump float;varying vec3 vN;varying vec2 vUv;varying float vDepth;" +
            "uniform vec3 uColor;uniform mat3 uRot;uniform float uAlpha;" +
            "uniform sampler2D uTex;uniform float uUseTex;uniform vec2 uUvRepeat;" +
            "uniform vec3 uSunDir0;uniform vec3 uSunColor0;uniform vec3 uSunDir1;uniform vec3 uSunColor1;" +
            "uniform vec3 uAmbSky;uniform vec3 uAmbGround;uniform vec3 uFogColor;uniform vec2 uFogRange;uniform float uFogOn;" +
            "vec3 aces(vec3 c){c*=1.1/0.6;" +
            "mat3 inM=mat3(vec3(0.59719,0.07600,0.02840),vec3(0.35458,0.90834,0.13383),vec3(0.04823,0.01566,0.83777));" +
            "mat3 outM=mat3(vec3(1.60475,-0.10208,-0.00327),vec3(-0.53108,1.10813,-0.07276),vec3(-0.07367,-0.00605,1.07602));" +
            "vec3 v=inM*c;vec3 num=v*(v+0.0245786)-0.000090537;vec3 den=v*(0.983729*v+0.4329510)+0.238081;" +
            "return clamp(outM*(num/den),0.0,1.0);}" +
            "void main(){vec4 tx=texture2D(uTex,vUv*uUvRepeat);" +
            "vec3 albedo=uColor*mix(vec3(1.0),pow(tx.rgb,vec3(2.2)),uUseTex);" +
            "float alpha=uAlpha*mix(1.0,tx.a,uUseTex);" +
            "vec3 n=normalize(vN);" +
            "vec3 l0=normalize(uSunDir0*uRot);vec3 l1=normalize(uSunDir1*uRot);" +
            "vec3 irr=uSunColor0*max(dot(n,l0),0.0)+uSunColor1*max(dot(n,l1),0.0);" +
            "irr+=mix(uAmbGround,uAmbSky,n.y*0.5+0.5);" +
            "vec3 c=aces(albedo*irr*0.31830989);" +
            "float f=uFogOn*clamp((vDepth-uFogRange.x)/max(uFogRange.y-uFogRange.x,0.001),0.0,1.0);" +
            "gl_FragColor=vec4(pow(mix(c,uFogColor,f),vec3(0.4545)),alpha);}"
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
        aUv = GLES20.glGetAttribLocation(program, "aUv")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uColor = GLES20.glGetUniformLocation(program, "uColor")
        uRot = GLES20.glGetUniformLocation(program, "uRot")
        uAlpha = GLES20.glGetUniformLocation(program, "uAlpha")
        uTex = GLES20.glGetUniformLocation(program, "uTex")
        uUseTex = GLES20.glGetUniformLocation(program, "uUseTex")
        uUvRepeat = GLES20.glGetUniformLocation(program, "uUvRepeat")
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

    // One step of the game: the scene's animation moves forward, a model attached to
    // an object follows the object it belongs to, and the scene's sounds are placed
    // around the player's head.
    fun update(listenerX: Float, listenerY: Float, listenerZ: Float, rightX: Float, rightZ: Float) {
        val now = seconds()
        var dt = if (lastFrame < 0f) 0f else now - lastFrame
        lastFrame = now
        if (dt < 0f) dt = 0f
        if (dt > 0.1f) dt = 0.1f
        if (dt > 0f) {
            for (i in scene.items.indices) {
                val item = scene.items[i]
                val anim = item.anim ?: continue
                var time = anim.time + dt * anim.speed
                if (time >= anim.duration) time = if (anim.loop) time % anim.duration else anim.duration
                anim.time = time
                moveTo(anim, item)
            }
        }
        for (i in scene.items.indices) {
            val item = scene.items[i]
            val parent = item.parent
            if (parent < 0 || parent >= i) continue
            System.arraycopy(scene.items[parent].matrix, 0, item.matrix, 0, 16)
            System.arraycopy(scene.items[parent].rotM, 0, item.rotM, 0, 9)
        }
        Audio.update(listenerX, listenerY, listenerZ, rightX, rightZ)
    }

    // Puts an animated object where its keyframes say it is at this moment. The
    // keyframes are evenly spaced across the duration, so the pair to draw between
    // comes from the time and the two are blended.
    private fun moveTo(anim: Scene.Anim, item: Scene.Item) {
        val last = anim.count - 1
        if (last < 0) return
        val span = if (last > 0) anim.duration / last else 0f
        var index = if (span > 0f) (anim.time / span).toInt() else 0
        if (index < 0) index = 0
        if (index > last) index = last
        var blend = 0f
        if (span > 0f) {
            blend = (anim.time - index * span) / span
            if (blend < 0f) blend = 0f
            if (blend > 1f) blend = 1f
        }
        val next = if (index < last) index + 1 else index
        for (k in 0 until 9) {
            val from = anim.kfs[index * 9 + k]
            val to = anim.kfs[next * 9 + k]
            val value = from + (to - from) * blend
            when (k) {
                0 -> item.pos[0] = value
                1 -> item.pos[1] = value
                2 -> item.pos[2] = value
                3 -> item.rot[0] = value
                4 -> item.rot[1] = value
                5 -> item.rot[2] = value
                6 -> item.scale[0] = value
                7 -> item.scale[1] = value
                8 -> item.scale[2] = value
            }
        }
        Scene.compose(item)
    }

    // The scene's sounds stop with the game.
    fun stopAudio() {
        Audio.stop()
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
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1i(uTex, 0)
        for (i in scene.items.indices) {
            val item = scene.items[i]
            if (item.blend != transparent) continue
            Matrix.multiplyMM(scratch, 0, view, 0, item.matrix, 0)
            Matrix.multiplyMM(mvp, 0, proj, 0, scratch, 0)
            GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
            GLES20.glUniformMatrix3fv(uRot, 1, false, item.rotM, 0)
            GLES20.glUniform2f(uUvRepeat, item.uv, item.uv)
            val model = if (item.model.isEmpty()) null else models[item.model]
            if (model != null) {
                // A model is drawn from both sides, exactly as the editor draws it.
                GLES20.glDisable(GLES20.GL_CULL_FACE)
                for (part in model.parts) {
                    // A texture the scene put on the object covers the model's own.
                    val named = if (item.tex.isNotEmpty()) item.tex else part.image
                    val texture = if (named.isEmpty()) 0 else textures[named] ?: 0
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (texture != 0) texture else white)
                    GLES20.glUniform1f(uUseTex, if (texture != 0) 1f else 0f)
                    GLES20.glUniform3f(uColor, part.tint[0], part.tint[1], part.tint[2])
                    GLES20.glUniform1f(uAlpha, part.tint[3] * item.opacity)
                    part.mesh.draw(aPos, aNormal, aUv, part.start, part.count)
                }
                GLES20.glEnable(GLES20.GL_CULL_FACE)
                continue
            }
            val mesh = shapes[item.shape] ?: continue
            val texture = if (item.tex.isEmpty()) 0 else textures[item.tex] ?: 0
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (texture != 0) texture else white)
            GLES20.glUniform1f(uUseTex, if (texture != 0) 1f else 0f)
            GLES20.glUniform3f(uColor, item.color[0], item.color[1], item.color[2])
            GLES20.glUniform1f(uAlpha, item.opacity)
            mesh.draw(aPos, aNormal, aUv)
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
