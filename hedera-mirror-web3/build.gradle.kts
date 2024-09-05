/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.net.HttpURLConnection
import java.net.URI

description = "Hedera Mirror Node Web3"

plugins {
    id("openapi-conventions")
    id("org.web3j")
    id("org.web3j.solidity")
    id("spring-conventions")
}

dependencies {
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies"))
    implementation(project(":common"))
    implementation("com.bucket4j:bucket4j-core")
    implementation("com.esaulpaugh:headlong")
    implementation("com.hedera.evm:hedera-evm")
    implementation("io.github.mweirauch:micrometer-jvm-extras")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("jakarta.inject:jakarta.inject-api")
    implementation("javax.inject:javax.inject:1")
    implementation("net.java.dev.jna:jna")
    implementation("org.bouncycastle:bcprov-jdk18on")
    implementation("org.springframework:spring-context-support")
    implementation("org.springframework.boot:spring-boot-actuator-autoconfigure")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.cloud:spring-cloud-starter-bootstrap")
    implementation("org.springframework.cloud:spring-cloud-starter-kubernetes-fabric8-config")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation(project(path = ":common", configuration = "testClasses"))
    testImplementation("io.vertx:vertx-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testImplementation("org.mockito:mockito-inline")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
}

web3j {
    generateBoth = true
    generatedPackageName = "com.hedera.mirror.web3.web3j.generated"
    useNativeJavaTypes = true
}

sourceSets { test { solidity { version = "0.8.24" } } }

tasks.bootRun { jvmArgs = listOf("--enable-preview") }

tasks.compileJava { options.compilerArgs.add("--enable-preview") }

tasks.test { jvmArgs = listOf("--enable-preview") }

val homeDir = System.getenv("HOME")
val web3jLink = file("$homeDir/.web3j/web3j")

val downloadWeb3j =
    tasks.register<Exec>("downloadWeb3j") {
        description = "Download and install Web3j CLI"
        group = "historical"

        commandLine("bash", "-c", "curl -L get.web3j.io | sh")
        onlyIf { !web3jLink.exists() }
    }

// Tasks to download OpenZeppelin contracts
val openZeppelinVersion = "4.9.3"
val openZeppelinFile = layout.buildDirectory.file("openzeppelin.zip").get().asFile
val openZeppelinDir =
    layout.projectDirectory.asFile
        .resolve("src")
        .resolve("test")
        .resolve("solidity_historical")
        .resolve("openzeppelin")

val downloadOpenZeppelin =
    tasks.register("downloadOpenZeppelin") {
        description = "Download OpenZeppelin contracts"
        group = "historical"
        doLast {
            openZeppelinDir.mkdirs()
            val openZeppelinUrl =
                "https://github.com/OpenZeppelin/openzeppelin-contracts/archive/v${openZeppelinVersion}.zip"
            val connection = URI(openZeppelinUrl).toURL().openConnection() as HttpURLConnection
            connection.inputStream.use { input ->
                openZeppelinFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        onlyIf { !openZeppelinFile.exists() }
    }

val extractOpenZeppelin =
    tasks.register<Copy>("extractContracts") {
        description = "Extracts the OpenZeppelin dependencies into the configured output folder"
        group = "historical"
        dependsOn(downloadOpenZeppelin)
        from(zipTree(openZeppelinFile))
        into(openZeppelinDir)
        include("openzeppelin-contracts-${openZeppelinVersion}/contracts/**/*.sol")
        eachFile {
            path = path.replaceFirst("openzeppelin-contracts-${openZeppelinVersion}/contracts", "")
        }
    }

// Task to compile Solidity contracts and generate Java files
val compileHistoricalSolidityContracts =
    tasks.register<Exec>("compileHistoricalSolidityContracts") {
        description = "Compiles the historical solidity contracts to java files using web3j-cli"
        group = "historical"
        mustRunAfter(tasks.named("generateTestContractWrappers"))
        dependsOn(downloadWeb3j)
        dependsOn(extractOpenZeppelin)
        dependsOn(tasks.named("compileTestSolidity"))
        val scriptPath = file("./src/main/resources/scripts/compile_solidity.sh").absolutePath
        doFirst { file(scriptPath).setExecutable(true) }
        commandLine("bash", scriptPath)
    }

tasks.assemble { dependsOn(tasks.processTestResources) }

tasks.compileTestJava {
    options.compilerArgs.add("--enable-preview")
    options.compilerArgs.removeIf { it == "-Werror" }
    dependsOn(compileHistoricalSolidityContracts)
}

tasks.openApiGenerate { mustRunAfter(tasks.named("resolveSolidity")) }

tasks.processTestResources {
    dependsOn(tasks.named("generateTestContractWrappers"))
    dependsOn(compileHistoricalSolidityContracts)
}
