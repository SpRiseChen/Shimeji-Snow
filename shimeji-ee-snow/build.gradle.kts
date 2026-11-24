plugins {
    java
    application
}

version = "1.0.0"
group = "com.group_finity"

// 国内镜像优先,海外回退 Maven Central
repositories {
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    mavenCentral()
}

dependencies {
    implementation("net.java.dev.jna:jna:5.19.1")
    // Advapi32Util 注册表读写(开机自启 HKCU Run 键)
    implementation("net.java.dev.jna:jna-platform:5.19.1")
    implementation("com.formdev:flatlaf:3.7.2")
    // 独立 Nashorn 行为脚本引擎。
    implementation("org.openjdk.nashorn:nashorn-core:15.7")
    // 热键、好感度与插件 JSON 解析。
    implementation("com.google.code.gson:gson:2.11.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// 多源码目录:核心、Windows、通用与窗口模式。
sourceSets.main {
    java.setSrcDirs(listOf("src", "src_win", "src_generic", "src_virtual"))
}

// 构建期版本戳:写入 classpath 资源,运行时由 Version.java 读取
// (gradlew run / fat jar / jpackage 三种形态读到同一份)
val versionPropsDir = layout.buildDirectory.dir("generated/version")
val generateVersionProps = tasks.register("generateVersionProps") {
    inputs.property("version", project.version)
    outputs.dir(versionPropsDir)
    doLast {
        val f = versionPropsDir.get().asFile.resolve("com/group_finity/mascot/version.properties")
        f.parentFile.mkdirs()
        f.writeText("version=${project.version}\n")
    }
}
sourceSets.main { resources.srcDir(versionPropsDir) }
tasks.named("processResources") { dependsOn(generateVersionProps) }

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

application {
    mainClass = "com.group_finity.mascot.Main"
    applicationDefaultJvmArgs = listOf("-Xmx512m")
}

tasks.named<JavaExec>("run") {
    // conf/ 与 img/ 挂到运行期 classpath:language_*.properties、schema、
    // logging.properties 均按 classpath 资源读取,且 conf 保持外置可编辑
    classpath = sourceSets.main.get().runtimeClasspath + files("conf", "img")
    workingDir = projectDir
}

// 独立可运行 fat jar:JNA/FlatLaf 并入,外置 conf/img 由 manifest Class-Path 指向
val fatJar = tasks.register<Jar>("fatJar") {
    archiveBaseName = "ShimejiSnow"
    archiveClassifier = ""
    archiveVersion = ""
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(
            "Main-Class" to "com.group_finity.mascot.Main",
            "Class-Path" to "./ conf/ img/",
        )
    }
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude(
            "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA",
            "META-INF/MANIFEST.MF", "module-info.class",
            // MR-JAR 的版本化内容不参与合并,基础类在 Java 21 下足够
            "META-INF/versions/**",
        )
    }
}

// jpackage 打包的输入目录:fat jar + 外置资源
val stageDir = layout.buildDirectory.dir("stage")

val stageApp = tasks.register<Sync>("stageApp") {
    group = "distribution"
    description = "组装 jpackage 输入目录(fat jar + conf + img + 许可文件 + 示例 mod)"
    into(stageDir)
    from(fatJar)
    // 运行时状态绝不入包:conf/ 里的用户层文件(settings.properties / preferences.properties /
    // affection.state.json / *.backup)都在 .gitignore 里,打包机上跑过一次应用就会有——
    // 不排除的话新用户会拿到打包者的设置。
    from("conf") {
        into("conf")
        exclude("settings.properties", "settings.properties.orig", "preferences.properties",
                "affection.state.json", "*.backup")
    }
    from("img") { into("img") }
    // 示例 mod 随包分发(Mods Guide 提到的 app\examples\;构建产物不入包)
    from("examples") {
        into("examples")
        exclude("**/build/**", "**/*.jar")
    }
    from(File(rootDir, "licence.txt"))
    from(File(rootDir, "originallicence.txt"))
    // Snow's own license accompanies the upstream notices in the distribution.
    from(File(rootDir.parentFile, "LICENSE"))
}

// Windows 绿色发行包:jpackage app-image(exe 在根,jar/conf/img 在 app\,精简 JRE 在 runtime\)
tasks.register<Exec>("packageApp") {
    group = "distribution"
    description = "用 jpackage 产出 app-image 绿色包(构建机需 JDK 21)"
    dependsOn(stageApp)
    val jpackageExe = javaToolchains.launcherFor(java.toolchain).get()
        .metadata.installationPath.dir("bin").file("jpackage.exe").asFile.absolutePath
    val outDir = layout.buildDirectory.dir("dist").get()
    doFirst {
        outDir.asFile.deleteRecursively()
    }
    commandLine(
        jpackageExe,
        "--type", "app-image",
        "--name", "ShimejiSnow",
        "--app-version", project.version.toString(),
        "--icon", "img/icon.ico",
        // jdeps 推导的精简模块集(Nashorn 需 scripting/dynalink,JNA 需 unsupported)
        "--add-modules", "java.base,java.desktop,java.logging,java.scripting,jdk.dynalink,jdk.unsupported",
        "--input", stageDir.get().asFile.absolutePath,
        "--main-jar", "ShimejiSnow.jar",
        "--main-class", "com.group_finity.mascot.Main",
        "--java-options", "-Xmx512m",
        "--dest", outDir.asFile.absolutePath,
    )
    // jpackage --input 只能落 app\,而 mods 目录按约定与 exe 同级
    doLast {
        val modsDir = File(outDir.asFile, "ShimejiSnow/mods")
        copy {
            from("guide")
            into(modsDir)
        }
        logger.lifecycle("mods guide copied to {}", modsDir)
    }
}

tasks.build {
    dependsOn(fatJar)
}
