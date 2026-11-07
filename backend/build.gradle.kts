plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.campuson"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Day 9: OS 환경변수가 yml을 덮어써서 빌드가 깨진 경험 → 테스트 프로필 고정.
    // SPRING_* 환경변수(예: 다른 프로젝트의 SPRING_DATASOURCE_URL)는 application-test.yml보다 우선하므로
    // 테스트 JVM에서 모두 제거해 테스트가 외부 DB에 붙는 사고를 막는다.
    environment = System.getenv().filterKeys { !it.startsWith("SPRING_") } + mapOf("SPRING_PROFILES_ACTIVE" to "test")
    // Mockito(inline mock maker)가 bootstrap classpath에 에이전트를 붙이면 JDK가 CDS 경고를 매번 찍는다.
    // 테스트 JVM에서만 CDS를 꺼 경고를 없앤다(기동 시간 차이는 무시할 수준).
    jvmArgs("-Xshare:off")
}

// bootRun은 로컬 개발 전용이다. 셸의 SPRING_*(다른 프로젝트의 prod 프로필·외부 DB)를 물려받으면
// local H2 대신 그 DB에 붙는다(Phase 1에서 실제로 발생). 운영 실행은 Docker 이미지(SPRING_PROFILES_ACTIVE=prod)가 맡는다.
// 다른 프로필이 필요하면 --args='--spring.profiles.active=...'로 명시한다.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    environment = System.getenv().filterKeys { !it.startsWith("SPRING_") }
}

// plain jar 제거: Dockerfile의 *.jar COPY가 파일 1개만 받게
tasks.jar {
    enabled = false
}
