import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Locale

plugins {
	id("net.fabricmc.fabric-loom-remap")
	id("org.jetbrains.kotlin.jvm") version "2.3.10"
	`maven-publish`
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

base {
	archivesName = providers.gradleProperty("archives_base_name")
}

val isWindowsHost = System.getProperty("os.name")
	.lowercase(Locale.ROOT)
	.contains("win")

val nativeBridgeProjectDir = layout.projectDirectory.dir("native/VisualMediaBridge")
val nativeBridgePublishDir = layout.buildDirectory.dir("native-bridge/win-x64")
val nativeBridgeResourcesDir = layout.buildDirectory.dir("generated/resources/main/native/win-x64")
val bundledLibrary by configurations.creating

val publishNativeMediaBridge by tasks.registering(Exec::class) {
	onlyIf { isWindowsHost }
	workingDir = nativeBridgeProjectDir.asFile
	commandLine(
		"dotnet",
		"publish",
		"VisualMediaBridge.csproj",
		"-c",
		"Release",
		"-r",
		"win-x64",
		"--self-contained",
		"true",
		"/p:PublishSingleFile=true",
		"/p:IncludeNativeLibrariesForSelfExtract=true",
		"/p:EnableCompressionInSingleFile=true",
		"/p:DebugType=None",
		"-o",
		nativeBridgePublishDir.get().asFile.absolutePath,
	)
	inputs.dir(nativeBridgeProjectDir)
	outputs.dir(nativeBridgePublishDir)
}

val stageNativeMediaBridge by tasks.registering(Copy::class) {
	onlyIf { isWindowsHost }
	dependsOn(publishNativeMediaBridge)
	from(nativeBridgePublishDir.map { it.file("VisualMediaBridge.exe") })
	into(nativeBridgeResourcesDir)
	rename { "visual-media-bridge.exe" }
}

repositories {
	mavenCentral()
	maven("https://maven.daqem.com/releases")
	maven("https://jitpack.io")
	maven("https://api.modrinth.com/maven") {
		name = "Modrinth"
		content {
			includeGroup("maven.modrinth")
		}
	}
}

dependencies {
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
	mappings(loom.officialMojangMappings())
	modImplementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")

	modImplementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
	modImplementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")
	modImplementation("com.daqem.uilib:uilib-fabric:19.1.0")
	implementation("javazoom:jlayer:1.0.1")
	bundledLibrary("javazoom:jlayer:1.0.1")
}

tasks.processResources {
	if (isWindowsHost) {
		dependsOn(stageNativeMediaBridge)
		from(nativeBridgeResourcesDir) {
			into("native/win-x64")
		}
	}

	inputs.property("version", version)
	filesMatching("fabric.mod.json") {
		expand("version" to version)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 21
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_21
	}
}

java {
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	inputs.property("archivesName", base.archivesName)
	from({
		bundledLibrary.files.map(::zipTree)
	})

	from("LICENSE") {
		rename { "${it}_${base.archivesName.get()}" }
	}
}

publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			artifactId = base.archivesName.get()
			from(components["java"])
		}
	}

	repositories {
	}
}

loom {
	runs {
		named("client") {
			vmArgs("-Xmx12G", "-Xms12G")
		}
	}
}
