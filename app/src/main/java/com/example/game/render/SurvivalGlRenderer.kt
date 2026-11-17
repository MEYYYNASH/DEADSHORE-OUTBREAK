package com.example.game.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import com.example.game.simulation.GameEngine
import com.example.game.simulation.ZombieAiState
import com.example.game.simulation.ZombieKind
import com.example.game.world.DoorSide
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * OpenGL ES 3.0 cel-shaded 3D renderer with inverted-hull dark ink outlines,
 * 512x512 low-resolution shadow map, warm point lights, flashlight spotlight,
 * frustum/distance culling, and animated first-person weapon viewmodels.
 */
class SurvivalGlRenderer(
  private val engine: GameEngine,
  private val onHudSnapshot: () -> Unit,
) : GLSurfaceView.Renderer {

  private class GpuMesh(val vboId: Int, val vertexCount: Int)

  private var shadowProg = 0
  private var celProg = 0
  private var outlineProg = 0

  private var shadowFbo = 0
  private var shadowTex = 0
  private var shadowReady = false

  private lateinit var townMesh: GpuMesh
  private lateinit var zombieWalkerMesh: GpuMesh
  private lateinit var zombieRunnerMesh: GpuMesh
  private lateinit var zombieArmedMesh: GpuMesh
  private lateinit var pickupMedMesh: GpuMesh
  private lateinit var pickupAmmoMesh: GpuMesh
  private lateinit var pickupFoodMesh: GpuMesh
  private lateinit var pickupSupplyMesh: GpuMesh
  private lateinit var tracerPlayerMesh: GpuMesh
  private lateinit var tracerEnemyMesh: GpuMesh
  private lateinit var muzzleFlashMesh: GpuMesh
  private lateinit var m4Mesh: GpuMesh
  private lateinit var glockMesh: GpuMesh
  private lateinit var batMesh: GpuMesh
  private lateinit var knifeMesh: GpuMesh
  private lateinit var tiendaSignMesh: GpuMesh
  private lateinit var panaderiaSignMesh: GpuMesh

  private var tiendaTexId = 0
  private var panaderiaTexId = 0

  private var viewWidth = 1280
  private var viewHeight = 720
  private var lastTimeNs = 0L
  private var hudAccumSec = 0f
  private var adsBlend = 0f
  private var dayMix = 0f

  // Pre-allocated matrices & arrays (zero GC inside onDrawFrame)
  private val projMatrix = FloatArray(16)
  private val viewMatrix = FloatArray(16)
  private val vpMatrix = FloatArray(16)
  private val modelMatrix = FloatArray(16)
  private val mvpMatrix = FloatArray(16)
  private val lightProj = FloatArray(16)
  private val lightView = FloatArray(16)
  private val lightVP = FloatArray(16)
  private val lightMVP = FloatArray(16)
  private val vmProj = FloatArray(16)
  private val lightPositionsFlat = FloatArray(24)

  // Uniform locations
  private var uShadowLightMVP = 0
  private var uCelMVP = 0
  private var uCelModel = 0
  private var uCelLightVP = 0
  private var uCelShadowMap = 0
  private var uCelAlbedoTex = 0
  private var uCelUseTex = 0
  private var uCelUnlit = 0
  private var uCelHasShadow = 0
  private var uCelDayMix = 0
  private var uCelSunDir = 0
  private var uCelCamPos = 0
  private var uCelCamDir = 0
  private var uCelFlashlightOn = 0
  private var uCelFogColor = 0
  private var uCelLightPos = 0
  private var uCelLightCount = 0

  private var uOutMVP = 0
  private var uOutModel = 0
  private var uOutWidth = 0
  private var uOutCamPos = 0
  private var uOutFogColor = 0

  override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
    GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    GLES30.glEnable(GLES30.GL_CULL_FACE)
    GLES30.glCullFace(GLES30.GL_BACK)

    shadowProg = CelShaders.compileProgram(CelShaders.SHADOW_VERTEX, CelShaders.SHADOW_FRAGMENT)
    celProg = CelShaders.compileProgram(CelShaders.CEL_VERTEX, CelShaders.CEL_FRAGMENT)
    outlineProg = CelShaders.compileProgram(CelShaders.OUTLINE_VERTEX, CelShaders.OUTLINE_FRAGMENT)

    cacheUniforms()
    initShadowFbo()
    uploadMeshes()
    lastTimeNs = System.nanoTime()
  }

  override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
    viewWidth = width.coerceAtLeast(1)
    viewHeight = height.coerceAtLeast(1)
    GLES30.glViewport(0, 0, viewWidth, viewHeight)
  }

  override fun onDrawFrame(gl: GL10?) {
    val now = System.nanoTime()
    val dt = if (lastTimeNs == 0L) 0.016f else ((now - lastTimeNs) / 1_000_000_000.0).toFloat().coerceIn(0.001f, 0.05f)
    lastTimeNs = now

    engine.step(dt)
    hudAccumSec += dt
    if (hudAccumSec >= 0.045f) {
      hudAccumSec = 0f
      onHudSnapshot()
    }

    val targetAds = if (engine.adsActive) 1f else 0f
    adsBlend += (targetAds - adsBlend) * (dt * 12f).coerceAtMost(1f)
    val targetDay = if (engine.dayMode) 1f else 0f
    dayMix += (targetDay - dayMix) * (dt * 4f).coerceAtMost(1f)

    // Camera basis
    val yawRad = engine.playerYawDeg * (PI.toFloat() / 180f)
    val pitchRad = engine.playerPitchDeg * (PI.toFloat() / 180f)
    val cosP = cos(pitchRad)
    val fwdX = sin(yawRad) * cosP
    val fwdY = sin(pitchRad)
    val fwdZ = -cos(yawRad) * cosP

    val bobAmp = if (engine.adsActive) 0.006f else 0.028f
    val bobY = sin(engine.walkBobPhase) * bobAmp * (if (absSpeedInput() > 0.1f) 1f else 0f)
    val camX = engine.playerX
    val camY = 1.65f + bobY
    val camZ = engine.playerZ

    val fov = 70f - 22f * adsBlend
    val aspect = viewWidth.toFloat() / viewHeight.toFloat()
    Matrix.perspectiveM(projMatrix, 0, fov, aspect, 0.12f, 110f)
    Matrix.setLookAtM(
      viewMatrix, 0,
      camX, camY, camZ,
      camX + fwdX, camY + fwdY, camZ + fwdZ,
      0f, 1f, 0f,
    )
    Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

    // Light VP for 512x512 low-res directional shadow map
    val sunX = if (dayMix > 0.5f) 28f else 22f
    val sunY = if (dayMix > 0.5f) 46f else 38f
    val sunZ = if (dayMix > 0.5f) 18f else 24f
    Matrix.orthoM(lightProj, 0, -48f, 48f, -48f, 48f, 5f, 120f)
    Matrix.setLookAtM(lightView, 0, sunX, sunY, sunZ, 0f, 0f, -4f, 0f, 1f, 0f)
    Matrix.multiplyMM(lightVP, 0, lightProj, 0, lightView, 0)

    // Pass 0: Shadow Map
    if (shadowReady) {
      renderShadowPass(camX, camZ, fwdX, fwdZ)
    }

    // Night indigo-purple vs coastal day sky
    val fogR = 0.085f + dayMix * 0.34f
    val fogG = 0.070f + dayMix * 0.44f
    val fogB = 0.175f + dayMix * 0.50f
    GLES30.glViewport(0, 0, viewWidth, viewHeight)
    GLES30.glClearColor(fogR, fogG, fogB, 1f)
    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

    // Gather 8 nearest street lamps
    val lamps = engine.world.lamps
    val lampCount = minOf(8, lamps.size)
    val sortedLamps =
      lamps.sortedBy { (it.x - camX) * (it.x - camX) + (it.z - camZ) * (it.z - camZ) }
    for (i in 0 until lampCount) {
      lightPositionsFlat[i * 3] = sortedLamps[i].x
      lightPositionsFlat[i * 3 + 1] = 4.0f
      lightPositionsFlat[i * 3 + 2] = sortedLamps[i].z
    }

    // Pass 1: Cel-Shaded World, Signboards, Pickups, Zombies, Tracers
    GLES30.glUseProgram(celProg)
    GLES30.glCullFace(GLES30.GL_BACK)
    GLES30.glUniformMatrix4fv(uCelLightVP, 1, false, lightVP, 0)
    GLES30.glUniform1f(uCelHasShadow, if (shadowReady) 1f else 0f)
    GLES30.glUniform1f(uCelDayMix, dayMix)
    GLES30.glUniform3f(uCelSunDir, sunX, sunY, sunZ)
    GLES30.glUniform3f(uCelCamPos, camX, camY, camZ)
    GLES30.glUniform3f(uCelCamDir, fwdX, fwdY, fwdZ)
    GLES30.glUniform1f(uCelFlashlightOn, if (engine.flashlightOn) 1f else 0f)
    GLES30.glUniform3f(uCelFogColor, fogR, fogG, fogB)
    GLES30.glUniform3fv(uCelLightPos, lampCount, lightPositionsFlat, 0)
    GLES30.glUniform1f(uCelLightCount, lampCount.toFloat())

    if (shadowReady) {
      GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
      GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadowTex)
      GLES30.glUniform1i(uCelShadowMap, 0)
    }

    // Static Town
    Matrix.setIdentityM(modelMatrix, 0)
    drawCelMesh(townMesh, useTex = 0f, unlit = 0f)

    // TIENDA & PANADERIA Signboards
    GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tiendaTexId)
    GLES30.glUniform1i(uCelAlbedoTex, 1)
    drawCelMesh(tiendaSignMesh, useTex = 1f, unlit = 1f)

    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, panaderiaTexId)
    GLES30.glUniform1i(uCelAlbedoTex, 1)
    drawCelMesh(panaderiaSignMesh, useTex = 1f, unlit = 1f)

    // Pickups (culled by distance & frustum)
    for (p in engine.pickups) {
      if (!p.active) continue
      if (!isVisible(p.x, p.z, camX, camZ, fwdX, fwdZ, 68f)) continue
      val yBob = 0.08f + sin(p.bobPhase) * 0.08f
      Matrix.setIdentityM(modelMatrix, 0)
      Matrix.translateM(modelMatrix, 0, p.x, yBob, p.z)
      Matrix.rotateM(modelMatrix, 0, p.bobPhase * 32f, 0f, 1f, 0f)
      val mesh =
        when {
          p.isSupplyDrop -> pickupSupplyMesh
          p.itemId == "medkit" -> pickupMedMesh
          p.itemId.startsWith("ammo") -> pickupAmmoMesh
          else -> pickupFoodMesh
        }
      drawCelMesh(mesh, useTex = 0f, unlit = if (p.isSupplyDrop) 1f else 0f)
    }

    // Zombies (culled by distance & frustum)
    for (z in engine.zombies) {
      if (!z.active) continue
      if (!isVisible(z.x, z.z, camX, camZ, fwdX, fwdZ, 70f)) continue
      Matrix.setIdentityM(modelMatrix, 0)
      Matrix.translateM(modelMatrix, 0, z.x, 0f, z.z)
      Matrix.rotateM(modelMatrix, 0, z.yawDeg, 0f, 1f, 0f)
      if (z.state == ZombieAiState.DEAD) {
        val collapse = ((0.75f - z.deathTimer) / 0.75f).coerceIn(0f, 1f)
        Matrix.rotateM(modelMatrix, 0, -collapse * 86f, 1f, 0f, 0f)
      } else {
        val sway = sin(z.animPhase) * 4.5f
        Matrix.rotateM(modelMatrix, 0, sway, 0f, 0f, 1f)
      }
      val mesh =
        when (z.kind) {
          ZombieKind.RUNNER -> zombieRunnerMesh
          ZombieKind.ARMED_RAIDER -> zombieArmedMesh
          ZombieKind.WALKER -> zombieWalkerMesh
        }
      drawCelMesh(mesh, useTex = 0f, unlit = if (z.hitFlashTimer > 0f) 1f else 0f)
    }

    // Bullet Tracers
    for (t in engine.tracers) {
      if (!t.active) continue
      val mx = (t.x0 + t.x1) * 0.5f
      val my = (t.y0 + t.y1) * 0.5f
      val mz = (t.z0 + t.z1) * 0.5f
      val dx = t.x1 - t.x0
      val dy = t.y1 - t.y0
      val dz = t.z1 - t.z0
      val len = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.1f)
      Matrix.setIdentityM(modelMatrix, 0)
      Matrix.translateM(modelMatrix, 0, mx, my, mz)
      val yaw = kotlin.math.atan2(dx, dz) * (180f / PI.toFloat())
      val pitch = -kotlin.math.asin((dy / len).coerceIn(-1f, 1f)) * (180f / PI.toFloat())
      Matrix.rotateM(modelMatrix, 0, yaw, 0f, 1f, 0f)
      Matrix.rotateM(modelMatrix, 0, pitch, 1f, 0f, 0f)
      Matrix.scaleM(modelMatrix, 0, if (t.isEnemy) 0.08f else 0.04f, if (t.isEnemy) 0.08f else 0.04f, len)
      drawCelMesh(if (t.isEnemy) tracerEnemyMesh else tracerPlayerMesh, useTex = 0f, unlit = 1f)
    }

    // Pass 2: Dark Ink Inverted-Hull Outlines
    GLES30.glUseProgram(outlineProg)
    GLES30.glCullFace(GLES30.GL_FRONT)
    GLES30.glUniform3f(uOutCamPos, camX, camY, camZ)
    GLES30.glUniform3f(uOutFogColor, fogR, fogG, fogB)
    Matrix.setIdentityM(modelMatrix, 0)
    drawOutlineMesh(townMesh, 0.022f)

    for (z in engine.zombies) {
      if (!z.active) continue
      if (!isVisible(z.x, z.z, camX, camZ, fwdX, fwdZ, 52f)) continue
      Matrix.setIdentityM(modelMatrix, 0)
      Matrix.translateM(modelMatrix, 0, z.x, 0f, z.z)
      Matrix.rotateM(modelMatrix, 0, z.yawDeg, 0f, 1f, 0f)
      if (z.state == ZombieAiState.DEAD) {
        val collapse = ((0.75f - z.deathTimer) / 0.75f).coerceIn(0f, 1f)
        Matrix.rotateM(modelMatrix, 0, -collapse * 86f, 1f, 0f, 0f)
      } else {
        Matrix.rotateM(modelMatrix, 0, sin(z.animPhase) * 4.5f, 0f, 0f, 1f)
      }
      drawOutlineMesh(zombieWalkerMesh, 0.018f)
    }

    // Pass 3: First-Person Weapon Viewmodel
    renderViewModel(aspect, fogR, fogG, fogB)
  }

  private fun renderShadowPass(camX: Float, camZ: Float, fwdX: Float, fwdZ: Float) {
    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFbo)
    GLES30.glViewport(0, 0, SHADOW_SIZE, SHADOW_SIZE)
    GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
    GLES30.glUseProgram(shadowProg)
    GLES30.glCullFace(GLES30.GL_FRONT)

    Matrix.setIdentityM(modelMatrix, 0)
    Matrix.multiplyMM(lightMVP, 0, lightVP, 0, modelMatrix, 0)
    GLES30.glUniformMatrix4fv(uShadowLightMVP, 1, false, lightMVP, 0)
    bindAndDrawPositionsOnly(townMesh)

    for (z in engine.zombies) {
      if (!z.active || z.state == ZombieAiState.DEAD) continue
      if (!isVisible(z.x, z.z, camX, camZ, fwdX, fwdZ, 55f)) continue
      Matrix.setIdentityM(modelMatrix, 0)
      Matrix.translateM(modelMatrix, 0, z.x, 0f, z.z)
      Matrix.rotateM(modelMatrix, 0, z.yawDeg, 0f, 1f, 0f)
      Matrix.multiplyMM(lightMVP, 0, lightVP, 0, modelMatrix, 0)
      GLES30.glUniformMatrix4fv(uShadowLightMVP, 1, false, lightMVP, 0)
      bindAndDrawPositionsOnly(zombieWalkerMesh)
    }

    GLES30.glCullFace(GLES30.GL_BACK)
    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
  }

  private fun renderViewModel(aspect: Float, fogR: Float, fogG: Float, fogB: Float) {
    val activeId = engine.hotbar.getOrNull(engine.activeHotbarSlot).orEmpty()
    val vmMesh =
      when (activeId) {
        "m4_carbine" -> m4Mesh
        "glock_17" -> glockMesh
        "baseball_bat" -> batMesh
        "knife" -> knifeMesh
        else -> return
      }

    GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
    Matrix.perspectiveM(vmProj, 0, 56f, aspect, 0.04f, 6f)

    val isMelee = activeId == "baseball_bat" || activeId == "knife"
    val swayX = sin(engine.walkBobPhase * 0.5f) * 0.012f * (1f - adsBlend * 0.7f)
    val swayY = kotlin.math.abs(cos(engine.walkBobPhase * 0.5f)) * 0.010f * (1f - adsBlend * 0.7f)

    val hipX = if (isMelee) 0.24f else 0.19f
    val hipY = if (isMelee) -0.18f else -0.15f
    val hipZ = if (isMelee) -0.44f else -0.34f

    val adsX = 0f
    val adsY = if (activeId == "m4_carbine") -0.072f else -0.056f
    val adsZ = -0.26f

    val posX = hipX + (adsX - hipX) * adsBlend + swayX
    val posY = hipY + (adsY - hipY) * adsBlend - swayY
    val posZ = hipZ + (adsZ - hipZ) * adsBlend + engine.recoilOffset

    Matrix.setIdentityM(modelMatrix, 0)
    Matrix.translateM(modelMatrix, 0, posX, posY, posZ)

    if (engine.isReloading) {
      val rp = sin((engine.reloadTimer / engine.reloadDuration) * PI.toFloat())
      Matrix.translateM(modelMatrix, 0, -0.04f * rp, -0.12f * rp, 0f)
      Matrix.rotateM(modelMatrix, 0, -34f * rp, 1f, 0f, 0f)
      Matrix.rotateM(modelMatrix, 0, 22f * rp, 0f, 0f, 1f)
    } else if (isMelee && engine.meleeSwingProgress > 0f) {
      val s = sin(engine.meleeSwingProgress * PI.toFloat())
      Matrix.rotateM(modelMatrix, 0, -68f * s, 1f, 0f, 0f)
      Matrix.rotateM(modelMatrix, 0, 48f * s, 0f, 1f, 0f)
    } else {
      Matrix.rotateM(modelMatrix, 0, engine.recoilOffset * 95f, 1f, 0f, 0f)
    }

    if (isMelee) {
      Matrix.rotateM(modelMatrix, 0, -18f, 1f, 0f, 0f)
      Matrix.rotateM(modelMatrix, 0, 14f, 0f, 0f, 1f)
    }

    Matrix.multiplyMM(mvpMatrix, 0, vmProj, 0, modelMatrix, 0)

    // Render viewmodel in camera-local space
    GLES30.glUseProgram(celProg)
    GLES30.glCullFace(GLES30.GL_BACK)
    GLES30.glUniformMatrix4fv(uCelMVP, 1, false, mvpMatrix, 0)
    GLES30.glUniformMatrix4fv(uCelModel, 1, false, modelMatrix, 0)
    GLES30.glUniform1f(uCelHasShadow, 0f)
    GLES30.glUniform1f(uCelUseTex, 0f)
    GLES30.glUniform1f(uCelUnlit, 0f)
    GLES30.glUniform3f(uCelCamPos, 0f, 0f, 0f)
    GLES30.glUniform3f(uCelCamDir, 0f, 0f, -1f)
    GLES30.glUniform3f(uCelSunDir, 1f, 2f, 1.5f)
    GLES30.glUniform1f(uCelLightCount, 0f)
    GLES30.glUniform3f(uCelFogColor, fogR, fogG, fogB)
    bindAndDrawFull(vmMesh)

    // Viewmodel dark outline
    GLES30.glUseProgram(outlineProg)
    GLES30.glCullFace(GLES30.GL_FRONT)
    GLES30.glUniformMatrix4fv(uOutMVP, 1, false, mvpMatrix, 0)
    GLES30.glUniformMatrix4fv(uOutModel, 1, false, modelMatrix, 0)
    GLES30.glUniform1f(uOutWidth, 0.0045f)
    GLES30.glUniform3f(uOutCamPos, 0f, 0f, 0f)
    GLES30.glUniform3f(uOutFogColor, fogR, fogG, fogB)
    bindAndDrawFull(vmMesh)
    GLES30.glCullFace(GLES30.GL_BACK)

    // Muzzle flash starburst
    if (!isMelee && engine.muzzleFlashTimer > 0f) {
      val barrelZ = if (activeId == "m4_carbine") -0.78f else -0.26f
      Matrix.translateM(modelMatrix, 0, 0f, 0.02f, barrelZ)
      Matrix.rotateM(modelMatrix, 0, engine.muzzleFlashTimer * 2400f, 0f, 0f, 1f)
      Matrix.scaleM(modelMatrix, 0, 0.14f, 0.14f, 0.14f)
      Matrix.multiplyMM(mvpMatrix, 0, vmProj, 0, modelMatrix, 0)
      GLES30.glUseProgram(celProg)
      GLES30.glUniformMatrix4fv(uCelMVP, 1, false, mvpMatrix, 0)
      GLES30.glUniformMatrix4fv(uCelModel, 1, false, modelMatrix, 0)
      GLES30.glUniform1f(uCelUnlit, 1f)
      bindAndDrawFull(muzzleFlashMesh)
    }
  }

  private fun drawCelMesh(mesh: GpuMesh, useTex: Float, unlit: Float) {
    Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
    GLES30.glUniformMatrix4fv(uCelMVP, 1, false, mvpMatrix, 0)
    GLES30.glUniformMatrix4fv(uCelModel, 1, false, modelMatrix, 0)
    GLES30.glUniform1f(uCelUseTex, useTex)
    GLES30.glUniform1f(uCelUnlit, unlit)
    bindAndDrawFull(mesh)
  }

  private fun drawOutlineMesh(mesh: GpuMesh, width: Float) {
    Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
    GLES30.glUniformMatrix4fv(uOutMVP, 1, false, mvpMatrix, 0)
    GLES30.glUniformMatrix4fv(uOutModel, 1, false, modelMatrix, 0)
    GLES30.glUniform1f(uOutWidth, width)
    bindAndDrawFull(mesh)
  }

  private fun bindAndDrawPositionsOnly(mesh: GpuMesh) {
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mesh.vboId)
    GLES30.glEnableVertexAttribArray(0)
    GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, MeshBuilder.STRIDE_BYTES, 0)
    GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, mesh.vertexCount)
  }

  private fun bindAndDrawFull(mesh: GpuMesh) {
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mesh.vboId)
    val stride = MeshBuilder.STRIDE_BYTES
    GLES30.glEnableVertexAttribArray(0)
    GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
    GLES30.glEnableVertexAttribArray(1)
    GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 12)
    GLES30.glEnableVertexAttribArray(2)
    GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, stride, 24)
    GLES30.glEnableVertexAttribArray(3)
    GLES30.glVertexAttribPointer(3, 2, GLES30.GL_FLOAT, false, stride, 36)
    GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, mesh.vertexCount)
  }

  private fun isVisible(x: Float, z: Float, camX: Float, camZ: Float, fwdX: Float, fwdZ: Float, maxDist: Float): Boolean {
    val dx = x - camX
    val dz = z - camZ
    val distSq = dx * dx + dz * dz
    if (distSq > maxDist * maxDist) return false
    if (distSq < 16f) return true
    val inv = 1f / sqrt(distSq)
    val dot = (dx * inv) * fwdX + (dz * inv) * fwdZ
    return dot > 0.15f
  }

  private fun absSpeedInput(): Float =
    sqrt(engine.moveInputX * engine.moveInputX + engine.moveInputY * engine.moveInputY)

  private fun cacheUniforms() {
    uShadowLightMVP = GLES30.glGetUniformLocation(shadowProg, "uLightMVP")

    uCelMVP = GLES30.glGetUniformLocation(celProg, "uMVP")
    uCelModel = GLES30.glGetUniformLocation(celProg, "uModel")
    uCelLightVP = GLES30.glGetUniformLocation(celProg, "uLightVP")
    uCelShadowMap = GLES30.glGetUniformLocation(celProg, "uShadowMap")
    uCelAlbedoTex = GLES30.glGetUniformLocation(celProg, "uAlbedoTex")
    uCelUseTex = GLES30.glGetUniformLocation(celProg, "uUseTex")
    uCelUnlit = GLES30.glGetUniformLocation(celProg, "uUnlit")
    uCelHasShadow = GLES30.glGetUniformLocation(celProg, "uHasShadow")
    uCelDayMix = GLES30.glGetUniformLocation(celProg, "uDayMix")
    uCelSunDir = GLES30.glGetUniformLocation(celProg, "uSunDir")
    uCelCamPos = GLES30.glGetUniformLocation(celProg, "uCamPos")
    uCelCamDir = GLES30.glGetUniformLocation(celProg, "uCamDir")
    uCelFlashlightOn = GLES30.glGetUniformLocation(celProg, "uFlashlightOn")
    uCelFogColor = GLES30.glGetUniformLocation(celProg, "uFogColor")
    uCelLightPos = GLES30.glGetUniformLocation(celProg, "uLightPos")
    uCelLightCount = GLES30.glGetUniformLocation(celProg, "uLightCount")

    uOutMVP = GLES30.glGetUniformLocation(outlineProg, "uMVP")
    uOutModel = GLES30.glGetUniformLocation(outlineProg, "uModel")
    uOutWidth = GLES30.glGetUniformLocation(outlineProg, "uOutlineWidth")
    uOutCamPos = GLES30.glGetUniformLocation(outlineProg, "uCamPos")
    uOutFogColor = GLES30.glGetUniformLocation(outlineProg, "uFogColor")
  }

  private fun initShadowFbo() {
    val fbos = IntArray(1)
    val texs = IntArray(1)
    GLES30.glGenFramebuffers(1, fbos, 0)
    GLES30.glGenTextures(1, texs, 0)
    shadowFbo = fbos[0]
    shadowTex = texs[0]

    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadowTex)
    GLES30.glTexImage2D(
      GLES30.GL_TEXTURE_2D, 0, GLES30.GL_DEPTH_COMPONENT16,
      SHADOW_SIZE, SHADOW_SIZE, 0, GLES30.GL_DEPTH_COMPONENT, GLES30.GL_UNSIGNED_SHORT, null,
    )
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFbo)
    GLES30.glFramebufferTexture2D(
      GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_TEXTURE_2D, shadowTex, 0,
    )
    shadowReady = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE
    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
  }

  private fun uploadMeshes() {
    townMesh = uploadVbo(WorldMeshFactory.buildTownMesh(engine.world))
    zombieWalkerMesh = uploadVbo(WorldMeshFactory.buildZombieMesh(isRunner = false, isArmed = false))
    zombieRunnerMesh = uploadVbo(WorldMeshFactory.buildZombieMesh(isRunner = true, isArmed = false))
    zombieArmedMesh = uploadVbo(WorldMeshFactory.buildZombieMesh(isRunner = false, isArmed = true))

    pickupMedMesh = uploadVbo(WorldMeshFactory.buildPickupBoxMesh(0.90f, 0.22f, 0.22f))
    pickupAmmoMesh = uploadVbo(WorldMeshFactory.buildPickupBoxMesh(0.86f, 0.68f, 0.20f))
    pickupFoodMesh = uploadVbo(WorldMeshFactory.buildPickupBoxMesh(0.24f, 0.66f, 0.85f))
    pickupSupplyMesh = uploadVbo(WorldMeshFactory.buildPickupBoxMesh(0.22f, 0.88f, 0.96f, tall = true))

    tracerPlayerMesh = uploadVbo(WorldMeshFactory.buildUnitBoxMesh(1.0f, 0.88f, 0.35f))
    tracerEnemyMesh = uploadVbo(WorldMeshFactory.buildUnitBoxMesh(1.0f, 0.22f, 0.22f))
    muzzleFlashMesh = uploadVbo(WorldMeshFactory.buildUnitBoxMesh(1.0f, 0.86f, 0.36f))

    m4Mesh = uploadVbo(WorldMeshFactory.buildM4ViewModel())
    glockMesh = uploadVbo(WorldMeshFactory.buildGlockViewModel())
    batMesh = uploadVbo(WorldMeshFactory.buildBatViewModel())
    knifeMesh = uploadVbo(WorldMeshFactory.buildKnifeViewModel())

    // TIENDA & PANADERIA glowing shop signs
    val tienda = engine.world.houses.first { it.signText == "TIENDA" }
    val panaderia = engine.world.houses.first { it.signText == "PANADERIA" }
    tiendaSignMesh =
      uploadVbo(
        WorldMeshFactory.buildSignQuad(
          x = tienda.maxX + 0.08f,
          y = 3.05f,
          z = (tienda.minZ + tienda.maxZ) * 0.5f,
          w = 4.2f,
          h = 0.95f,
          facingEast = tienda.doorSide == DoorSide.EAST,
        )
      )
    panaderiaSignMesh =
      uploadVbo(
        WorldMeshFactory.buildSignQuad(
          x = panaderia.minX - 0.08f,
          y = 3.05f,
          z = (panaderia.minZ + panaderia.maxZ) * 0.5f,
          w = 4.6f,
          h = 0.95f,
          facingEast = panaderia.doorSide == DoorSide.EAST,
        )
      )
    tiendaTexId = createSignTexture("TIENDA", Color.rgb(30, 82, 86), Color.rgb(255, 214, 102))
    panaderiaTexId = createSignTexture("PANADERIA", Color.rgb(122, 46, 28), Color.rgb(255, 224, 140))
  }

  private fun uploadVbo(mb: MeshBuilder): GpuMesh {
    val ids = IntArray(1)
    GLES30.glGenBuffers(1, ids, 0)
    val id = ids[0]
    val buf = mb.toFloatBuffer()
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, id)
    GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, buf.capacity() * 4, buf, GLES30.GL_STATIC_DRAW)
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    return GpuMesh(id, mb.vertexCount)
  }

  private fun createSignTexture(text: String, bgColor: Int, fgColor: Int): Int {
    val bmp = Bitmap.createBitmap(256, 64, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(bgColor)
    val borderPaint =
      Paint().apply {
        color = fgColor
        style = Paint.Style.STROKE
        strokeWidth = 6f
      }
    canvas.drawRect(4f, 4f, 252f, 60f, borderPaint)
    val textPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fgColor
        textSize = 36f
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
      }
    canvas.drawText(text, 128f, 44f, textPaint)

    val ids = IntArray(1)
    GLES30.glGenTextures(1, ids, 0)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
    GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
    bmp.recycle()
    return ids[0]
  }

  private companion object {
    const val SHADOW_SIZE = 512
  }
}
