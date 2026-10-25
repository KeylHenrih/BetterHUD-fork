# Hardware GPU Shader Verification Harness

This standalone test suite verifies generated BetterHud client animation GLSL vertex shaders on a physical GPU using OpenGL 4.6 Core Profile, headless GLFW windowing, and GPU Transform Feedback buffers.

> **Note:** This harness is an optional hardware diagnostic tool. It is **not** included in the standard `./gradlew build` lifecycle and does not run in headless CI.

---

## 1. Prerequisites

1. **JDK 25 or higher:**
   Ensure `JAVA_HOME` is set or `java` is in your `PATH`.
2. **GPU Driver with OpenGL 4.6 Core Profile:**
   - Linux: Mesa 22.0+ (Iris, RadeonSI, Nouveau, NVK) or proprietary NVIDIA driver.
   - Display: Accessible X11 / Wayland display or headless EGL / GLX context.
3. **Built Project:**
   The test dynamically references compiled classes and dependencies. Run `./gradlew build` once before running this test.

---

## 2. Running the Test

```bash
# Optional: set JAVA_HOME if not in PATH
export JAVA_HOME=/path/to/jdk-25+

# Execute the test
./tools/gpu-shader-test/run_gpu_test.sh
```

---

## 3. What the Test Verifies

1. **Generated GLSL Compilation:**
   Invokes `GlslTranspiler` and `ShaderManagerImpl.generateShaderLayout` to generate real shader code from YAML popup and layout definitions.
2. **Hardware Transform Feedback:**
   Compiles and links the vertex shader into an OpenGL 4.6 program with `glTransformFeedbackVaryings`.
3. **Numerical Accuracy:**
   Streams 1,000,000 vertices across multiple animation time steps ($t = 0, 5, 10, \dots$) and compares GPU-transformed vertex positions and unpacked colors against mathematical expectations:
   - Position discrepancy: $< 0.0001\text{ px}$.
   - Max color channel error: $0.0\text{ levels}$.
