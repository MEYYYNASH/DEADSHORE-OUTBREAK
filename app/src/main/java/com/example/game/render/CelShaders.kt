package com.example.game.render

import android.opengl.GLES30
import android.util.Log

object CelShaders {
  const val SHADOW_VERTEX = """#version 300 es
precision highp float;
layout(location = 0) in vec3 aPos;
uniform mat4 uLightMVP;
void main() {
  gl_Position = uLightMVP * vec4(aPos, 1.0);
}
"""

  const val SHADOW_FRAGMENT = """#version 300 es
precision mediump float;
out vec4 fragColor;
void main() {
  fragColor = vec4(1.0);
}
"""

  const val CEL_VERTEX = """#version 300 es
precision highp float;
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec3 aColor;
layout(location = 3) in vec2 aUv;

uniform mat4 uMVP;
uniform mat4 uModel;
uniform mat4 uLightVP;

out vec3 vWorldPos;
out vec3 vWorldNormal;
out vec3 vColor;
out vec2 vUv;
out vec4 vLightSpacePos;

void main() {
  vec4 worldPos = uModel * vec4(aPos, 1.0);
  vWorldPos = worldPos.xyz;
  vWorldNormal = normalize(mat3(uModel) * aNormal);
  vColor = aColor;
  vUv = aUv;
  vLightSpacePos = uLightVP * worldPos;
  gl_Position = uMVP * vec4(aPos, 1.0);
}
"""

  const val CEL_FRAGMENT = """#version 300 es
precision highp float;

in vec3 vWorldPos;
in vec3 vWorldNormal;
in vec3 vColor;
in vec2 vUv;
in vec4 vLightSpacePos;

uniform sampler2D uShadowMap;
uniform sampler2D uAlbedoTex;
uniform float uUseTex;
uniform float uUnlit;
uniform float uHasShadow;
uniform float uDayMix;

uniform vec3 uSunDir;
uniform vec3 uCamPos;
uniform vec3 uCamDir;
uniform float uFlashlightOn;
uniform vec3 uFogColor;
uniform vec3 uLightPos[8];
uniform float uLightCount;

out vec4 fragColor;

float computeShadow(vec4 lightSpacePos, vec3 n) {
  if (uHasShadow < 0.5) return 1.0;
  vec3 proj = lightSpacePos.xyz / lightSpacePos.w;
  proj = proj * 0.5 + 0.5;
  if (proj.x < 0.01 || proj.x > 0.99 || proj.y < 0.01 || proj.y > 0.99 || proj.z > 1.0) {
    return 1.0;
  }
  float closestDepth = texture(uShadowMap, proj.xy).r;
  float bias = max(0.0045 * (1.0 - dot(n, uSunDir)), 0.002);
  return (proj.z - bias) > closestDepth ? 0.42 : 1.0;
}

float celQuantize(float val) {
  if (val > 0.65) return 1.0;
  if (val > 0.22) return 0.62;
  return 0.28;
}

void main() {
  vec3 baseColor = vColor;
  if (uUseTex > 0.5) {
    vec4 tex = texture(uAlbedoTex, vUv);
    baseColor *= tex.rgb;
  }

  // Emissive warm windows, street lantern bulbs, red-dot, muzzle flashes, tracers
  bool isEmissive = uUnlit > 0.5 || (vColor.r > 0.95 && vColor.g > 0.72 && vColor.b < 0.50);
  if (isEmissive) {
    float dist = length(vWorldPos - uCamPos);
    float fog = clamp((dist - 22.0) / 55.0, 0.0, 0.75);
    vec3 col = mix(baseColor * 1.15, uFogColor, fog);
    fragColor = vec4(col, 1.0);
    return;
  }

  vec3 n = normalize(vWorldNormal);
  float ndl = max(dot(n, normalize(uSunDir)), 0.0);
  float shadow = computeShadow(vLightSpacePos, n);
  float celDir = celQuantize(ndl * shadow);

  vec3 nightAmbient = vec3(0.20, 0.18, 0.36);
  vec3 dayAmbient = vec3(0.46, 0.52, 0.64);
  vec3 ambient = mix(nightAmbient, dayAmbient, uDayMix);

  vec3 nightDirColor = vec3(0.38, 0.40, 0.72);
  vec3 dayDirColor = vec3(0.95, 0.88, 0.72);
  vec3 dirColor = mix(nightDirColor, dayDirColor, uDayMix) * celDir;

  // Warm street lamp & window point lights
  vec3 pointAccum = vec3(0.0);
  int count = int(uLightCount);
  for (int i = 0; i < 8; i++) {
    if (i >= count) break;
    vec3 toLight = uLightPos[i] - vWorldPos;
    float d = length(toLight);
    if (d < 13.5) {
      float atten = 1.0 - smoothstep(1.5, 13.5, d);
      float pDot = max(dot(n, toLight / max(d, 0.01)), 0.0);
      float q = (pDot * atten) > 0.32 ? 1.0 : ((pDot * atten) > 0.10 ? 0.55 : 0.0);
      pointAccum += vec3(1.0, 0.68, 0.30) * q * (1.0 - uDayMix * 0.65);
    }
  }

  // Player tactical flashlight spotlight
  vec3 flashAccum = vec3(0.0);
  if (uFlashlightOn > 0.5) {
    vec3 toFrag = vWorldPos - uCamPos;
    float fd = length(toFrag);
    if (fd > 0.2 && fd < 26.0) {
      vec3 dirToFrag = toFrag / fd;
      float cone = dot(dirToFrag, normalize(uCamDir));
      if (cone > 0.84) {
        float spot = smoothstep(0.84, 0.95, cone) * (1.0 - smoothstep(5.0, 26.0, fd));
        float q = spot > 0.42 ? 0.95 : 0.45;
        flashAccum = vec3(0.96, 0.94, 0.82) * q * (1.0 - uDayMix * 0.5);
      }
    }
  }

  vec3 lit = baseColor * (ambient + dirColor + pointAccum * 0.75 + flashAccum * 0.85);

  float camDist = length(vWorldPos - uCamPos);
  float fogFactor = clamp((camDist - 18.0) / 56.0, 0.0, 0.88);
  vec3 finalCol = mix(lit, uFogColor, fogFactor);
  fragColor = vec4(finalCol, 1.0);
}
"""

  const val OUTLINE_VERTEX = """#version 300 es
precision highp float;
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;

uniform mat4 uMVP;
uniform mat4 uModel;
uniform float uOutlineWidth;
uniform vec3 uCamPos;

out float vCamDist;

void main() {
  vec4 worldPos = uModel * vec4(aPos, 1.0);
  vCamDist = length(worldPos.xyz - uCamPos);
  float scale = clamp(vCamDist * 0.035, 0.45, 2.2);
  vec3 extruded = aPos + normalize(aNormal) * (uOutlineWidth * scale);
  gl_Position = uMVP * vec4(extruded, 1.0);
}
"""

  const val OUTLINE_FRAGMENT = """#version 300 es
precision mediump float;
in float vCamDist;
uniform vec3 uFogColor;
out vec4 fragColor;

void main() {
  vec3 ink = vec3(0.035, 0.028, 0.065);
  float fogFactor = clamp((vCamDist - 18.0) / 56.0, 0.0, 0.88);
  fragColor = vec4(mix(ink, uFogColor, fogFactor), 1.0);
}
"""

  fun compileProgram(vertSrc: String, fragSrc: String): Int {
    val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertSrc)
    val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragSrc)
    val prog = GLES30.glCreateProgram()
    GLES30.glAttachShader(prog, vs)
    GLES30.glAttachShader(prog, fs)
    GLES30.glLinkProgram(prog)
    val status = IntArray(1)
    GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, status, 0)
    if (status[0] == 0) {
      Log.e("CelShaders", "Program link failed: ${GLES30.glGetProgramInfoLog(prog)}")
    }
    GLES30.glDeleteShader(vs)
    GLES30.glDeleteShader(fs)
    return prog
  }

  private fun compileShader(type: Int, src: String): Int {
    val shader = GLES30.glCreateShader(type)
    GLES30.glShaderSource(shader, src)
    GLES30.glCompileShader(shader)
    val status = IntArray(1)
    GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
    if (status[0] == 0) {
      Log.e("CelShaders", "Shader compile failed: ${GLES30.glGetShaderInfoLog(shader)}")
    }
    return shader
  }
}
