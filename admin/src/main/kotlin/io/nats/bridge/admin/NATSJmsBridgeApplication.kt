package io.nats.bridge.admin

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.nats.bridge.admin.util.ClasspathUtils
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import java.io.File
import java.lang.IllegalStateException
import java.util.concurrent.atomic.AtomicReference

@SpringBootApplication
open class NATSJmsBridgeApplication

object AppConfig {
    private val applicationConfigRef = AtomicReference<ApplicationConfig?>()

    fun setConfig(appConfig:ApplicationConfig) {
        if (!runSpringBootDirect) {
            applicationConfigRef.set(appConfig)
        } else {
            if (!applicationConfigRef.compareAndSet(null, appConfig)) {
                throw IllegalStateException("Application config can only be set once")
            }
        }
    }

    fun getConfig() : ApplicationConfig =applicationConfigRef.get()
                ?: throw IllegalStateException("Application config must bet set before running application")

    @JvmStatic
    fun runner() = Run(runSpringBootDirect)


    var configFolderDefault: String = "./config/"
    var bridgeConfigFileDefault: String = "nats-bridge.yaml"
    var loginConfigFileDefault: String = "nats-bridge-logins.yaml"
    var runSpringBootDirect: Boolean = true

}

data class ApplicationConfig(val bridgeConfigFile:String, val loginConfigFile:String, val configDirectory:String?)

open class Run(runBoot:Boolean) : CliktCommand(help = "Run NATS JMS/IBM MQ Bridge", epilog="""
    You can also set environments variables by replacing dashes '-' with underscores '_' and prefix with "NATS_BRIDGE" 
    
    ```
    NATS_BRIDGE_LOGIN_CONFIG_FILE=./config/nats-bridge-logins.yaml
    NATS_BRIDGE_BRIDGE_CONFIG_FILE=./config/nats-bridge.yaml
    ```
    
    Files can also be on the classpath inside of a jar file or on the file system in the classpath. 
    Just prepend the file name with "classpath://" to denote looking for this file on the classpath instead of the file system.
    
    ```
    -f classpath://nats-bridge.yaml
    ```
    
""".trimIndent()) {

    init {
        context { autoEnvvarPrefix = "NATS_BRIDGE" }
    }


    private val configFolder: String? by option("-d", "--config-directory", help = "Location of Configuration Directory")
            .default(AppConfig.configFolderDefault)
    private val bridgeConfigFile: String? by option("-f", "--bridge-config-file", help = "Location of Bridge Config File")
            .default(AppConfig.bridgeConfigFileDefault)
    private val loginConfigFile: String? by option("-l", "--login-config-file", help = "Location of Bridge Login Config File")
            .default(AppConfig.loginConfigFileDefault)

    private val bootApp by option("--on", "-o").flag("--off", "-ff", default = runBoot)




    open fun config() {


        val configFileLocation : String = readFileConf(bridgeConfigFile, configFolder!! )
        val loginConfigLocation : String = readFileConf(loginConfigFile, configFolder!!)
        AppConfig.setConfig(ApplicationConfig(configFileLocation, loginConfigLocation, configFolder))
    }

    override fun run() {
        config()
        if (bootApp)  SpringApplication.run(NATSJmsBridgeApplication::class.java, *args!!)
    }

    private fun readFileConf(configFileLocation:String?, configFolder:String): String {

        println("CONFIG FILE LOCATION $configFileLocation")
        println("CONFIG FOLDER $configFolder")



        return if (configFileLocation.isNullOrBlank() || !configFileLocation.startsWith("classpath:")) {
            val configDir = File(configFolder)

            if (!configDir.exists() ) {
                try {
                    configDir.mkdirs()
                } catch (ex: Exception) {
                    echo("Configuration directory does not exist and could not be created")
                }
            }

            if (configDir.exists()) {
                val configFile = File(configDir, configFileLocation)
                if (configFile.exists()) {
                    echo("Using configuration file $configFile")
                    configFile.toString()
                } else {
                    if (configFile.parentFile.canWrite()) {
                        echo("Using configuration file $configFile which does not exist but can be written to")
                        configFile.toString()
                    } else {
                        echo("Trying to use configuration file $configFile but it is not writeable so using instead classpath://./config/$configFileLocation")
                        val paths = ClasspathUtils.paths(this.javaClass, "./config/$configFileLocation")
                        if (paths.isEmpty()) {
                            echo("No configuration is found, exiting")
                            throw IllegalStateException("No configuration is found, exiting; \n " +
                                    "Tried to use configuration file $configFile but it is not writeable so using classpath://./config/$configFileLocation instead")
                        } else {
                            "$configFileLocation"
                        }
                    }
                }
            } else {
                throw IllegalStateException("No configuration is found, exiting $configFileLocation $configDir")
            }
        } else {
            println("CLASSPATH RESOURCE $configFileLocation")
            configFileLocation
        }
    }

    private var args:Array<String>? = null

    open fun runMain(args:Array<String>) {
        this.args = args
        super.main(args)
    }



}

object ApplicationMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val runner = AppConfig.runner()
        runner.runMain(args)
    }
}

