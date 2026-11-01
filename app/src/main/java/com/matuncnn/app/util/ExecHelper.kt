package com.matuncnn.app.util

import android.util.Log
import java.io.File

object ExecHelper {
    private const val TAG = "ExecHelper"
    private var method: ExecMethod? = null

    private enum class ExecMethod {
        DIRECT, LINKER64
    }

    fun exec(
        binary: File,
        args: List<String>,
        workDir: String,
        extraEnv: Map<String, String> = emptyMap()
    ): Process {
        if (method == null) {
            method = probeMethod(binary, workDir)
        }
        DebugLog.log("Exec", "${method} ${binary.name} ${args.joinToString(" ")}")
        return when (method) {
            ExecMethod.DIRECT -> execDirect(binary, args, workDir, extraEnv)
            ExecMethod.LINKER64 -> execLinker64(binary, args, workDir, extraEnv)
            null -> throw RuntimeException("All execution methods failed")
        }
    }

    private fun probeMethod(binary: File, workDir: String): ExecMethod {
        if (tryMethod { execDirect(binary, emptyList(), workDir).apply { destroy(); waitFor() } }) {
            DebugLog.log("Exec", "Probe: DIRECT works for ${binary.name}")
            return ExecMethod.DIRECT
        }
        DebugLog.log("Exec", "Probe: direct execution failed, trying linker64")
        Log.i(TAG, "Direct exec failed, trying linker64...")
        if (tryMethod { execLinker64(binary, emptyList(), workDir).apply { destroy(); waitFor() } }) {
            DebugLog.log("Exec", "Probe: LINKER64 works for ${binary.name}")
            return ExecMethod.LINKER64
        }

        DebugLog.log("Exec", "Probe: ALL methods failed for ${binary.name}")
        throw RuntimeException("Cannot execute binary: ${binary.absolutePath} (noexec mount)")
    }

    private fun execDirect(binary: File, args: List<String>, workDir: String, extraEnv: Map<String, String> = emptyMap()): Process {
        val pb = ProcessBuilder(listOf(binary.absolutePath) + args)
        pb.directory(File(workDir))
        pb.environment()["LD_LIBRARY_PATH"] = workDir
        pb.environment().putAll(extraEnv)
        pb.redirectErrorStream(true)
        return pb.start()
    }

    private fun execLinker64(binary: File, args: List<String>, workDir: String, extraEnv: Map<String, String> = emptyMap()): Process {
        val is64 = android.os.Build.SUPPORTED_64_BIT_ABIS?.isNotEmpty() ?: true
        val linker = if (is64) "/system/bin/linker64" else "/system/bin/linker"
        val pb = ProcessBuilder(listOf(linker, binary.absolutePath) + args)
        pb.directory(File(workDir))
        pb.environment()["LD_LIBRARY_PATH"] = workDir
        pb.environment().putAll(extraEnv)
        pb.redirectErrorStream(true)
        return pb.start()
    }

    private fun tryMethod(block: () -> Any?): Boolean {
        return try {
            block()
            true
        } catch (_: Exception) {
            false
        }
    }
}
