package com.frenchef.intellijlsp.language

import com.frenchef.intellijlsp.language.xml.XmlLanguageHandler
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.psi.PsiFile

/**
 * 语言处理器注册表 - 管理语言处理器的注册和查找
 * 
 * 按照优先级顺序查找能处理指定文件的处理器。
 * JVM 处理器优先，通用处理器作为回退方案。
 * 
 * 采用懒加载策略：只有在对应的语言插件存在时才实例化处理器，
 * 避免在没有安装 Go/Rust/JS 插件的 IDE 中触发 ClassNotFoundException。
 */
object LanguageHandlerRegistry {
    private val log = logger<LanguageHandlerRegistry>()
    private val handlers = mutableListOf<LanguageHandler>()
    private val genericHandler = GenericLanguageHandler()

    // 语言插件的 PluginId 映射
    private object PluginIds {
        const val GO = "org.jetbrains.plugins.go"
        const val RUST = "org.rust.lang"
        const val JAVASCRIPT = "JavaScript"
    }

    init {
        // 注册默认处理器（按优先级顺序，越靠前优先级越高）
        // JVM 和 XML 是平台内置的，无需检查
        registerDefault(JvmLanguageHandler())
        registerDefault(XmlLanguageHandler())

        // 可选语言插件：只有在插件存在时才注册
        tryRegisterOptionalHandler(PluginIds.JAVASCRIPT) {
            Class.forName("com.frenchef.intellijlsp.language.js.JavaScriptLanguageHandler")
                .getDeclaredConstructor().newInstance() as LanguageHandler
        }

        tryRegisterOptionalHandler(PluginIds.GO) {
            Class.forName("com.frenchef.intellijlsp.language.go.GoLanguageHandler")
                .getDeclaredConstructor().newInstance() as LanguageHandler
        }

        tryRegisterOptionalHandler(PluginIds.RUST) {
            Class.forName("com.frenchef.intellijlsp.language.rust.RustLanguageHandler")
                .getDeclaredConstructor().newInstance() as LanguageHandler
        }
        
        // GenericLanguageHandler 作为回退，不添加到列表
    }

    /**
     * 注册语言处理器
     * 新注册的处理器优先级高于已有处理器
     */
    fun register(handler: LanguageHandler) {
        handlers.add(0, handler)
        log.info("Registered LanguageHandler (high priority): ${handler.javaClass.simpleName}")
    }

    /**
     * 注册默认语言处理器
     * 默认处理器按照注册顺序决定优先级（先注册者优先）
     */
    private fun registerDefault(handler: LanguageHandler) {
        handlers.add(handler)
        log.info("Registered LanguageHandler (default): ${handler.javaClass.simpleName}")
    }

    /**
     * 尝试注册可选语言插件的处理器
     * 只有在对应插件已安装且启用时才会实例化处理器
     */
    private fun tryRegisterOptionalHandler(pluginId: String, factory: () -> LanguageHandler) {
        try {
            val plugin = PluginManagerCore.getPlugin(PluginId.getId(pluginId))
            if (plugin != null && plugin.isEnabled) {
                val handler = factory()
                handlers.add(handler)
                log.info("Registered optional LanguageHandler: ${handler.javaClass.simpleName} (plugin: $pluginId)")
            } else {
                log.info("Skipped LanguageHandler for plugin $pluginId (not installed or disabled)")
            }
        } catch (e: ClassNotFoundException) {
            log.info("Skipped LanguageHandler for plugin $pluginId (class not found: ${e.message})")
        } catch (e: NoClassDefFoundError) {
            log.info("Skipped LanguageHandler for plugin $pluginId (dependency not found: ${e.message})")
        } catch (e: Exception) {
            log.warn("Failed to register LanguageHandler for plugin $pluginId: ${e.message}")
        }
    }

    /**
     * 获取能处理指定文件的语言处理器
     * 如果没有专用处理器，返回通用处理器
     */
    fun getHandler(file: PsiFile): LanguageHandler {
        val handler = handlers.find { it.supports(file) }
        if (handler != null) {
            log.debug("Using ${handler.javaClass.simpleName} for ${file.language.id}")
            return handler
        }
        log.debug("Using GenericLanguageHandler (fallback) for ${file.language.id}")
        return genericHandler
    }

    /**
     * 检查是否有专用处理器支持该文件
     */
    fun hasSpecializedHandler(file: PsiFile): Boolean {
        return handlers.any { it.supports(file) }
    }
}
