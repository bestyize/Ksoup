# Ksoup

Ksoup 是一个 Kotlin Multiplatform HTML 解析库，目标是提供接近 jsoup 使用习惯的 API，并支持在 Android、iOS 和 JVM/Desktop 上复用同一套解析与抓取逻辑。

当前仓库包含三个主要模块：

- `ksoup`: 核心库，负责 HTML/XML 解析、选择器、清洗和 HTTP 连接能力
- `composeApp`: Compose Multiplatform 示例 UI，共享 Android、iOS 和 Desktop 的界面代码
- `androidApp`: Android 入口应用，用来承载 `composeApp`

## 特性

- HTML 文档解析与 DOM 操作
- CSS Selector 查询
- HTML 片段解析
- XML 解析
- Safelist 清洗和内容校验
- 基于 Ktor 的跨平台 HTTP 抓取
- 会话、Cookie、表单、上传、代理、TLS 配置

## 支持平台

- Android
- iOS
- JVM/Desktop

## 安装

### 1. 在 Kotlin Multiplatform 工程中引用

先确保仓库列表里包含 `mavenLocal()` 或你的私有 Maven 仓库：

```kotlin
repositories {
    mavenLocal()
    google()
    mavenCentral()
}
```

然后在 `commonMain` 中引用：

```kotlin
dependencies {
    implementation("xyz.thewind.ksoup:ksoup:0.1.0-SNAPSHOT")
}
```

这个坐标会通过 Gradle Metadata 自动解析到各平台变体。

### 2. 在 Spring 或普通 JVM 工程中引用

如果是 Spring Boot、普通 Kotlin/JVM 或 Java 工程，建议直接引用 JVM 产物：

```kotlin
dependencies {
    implementation("xyz.thewind.ksoup:ksoup-jvm:0.1.0-SNAPSHOT")
}
```

如果是 Maven 工程：

```xml
<repositories>
    <repository>
        <id>local-maven</id>
        <url>file://${user.home}/.m2/repository</url>
    </repository>
</repositories>

<dependency>
    <groupId>xyz.thewind.ksoup</groupId>
    <artifactId>ksoup-jvm</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## 快速开始

### 解析 HTML

```kotlin
import xyz.thewind.ksoup.Jsoup

val html = """
    <html>
      <head><title>Ksoup Demo</title></head>
      <body>
        <section id="news">
          <article class="card featured">
            <h2>Ksoup milestone</h2>
            <p>Parse HTML on Android, iOS and Desktop.</p>
          </article>
        </section>
      </body>
    </html>
""".trimIndent()

val document = Jsoup.parse(html)

println(document.title())
println(document.select("#news > article.featured h2").text())
println(document.body().text())
```

### 解析 HTML 片段

```kotlin
val fragment = Jsoup.parseBodyFragment("<p>Hello <b>world</b></p>")
println(fragment.body().html())
```

### 使用选择器

```kotlin
val doc = Jsoup.parse(
    """
    <div id="root">
      <p class="lead">Hello</p>
      <p data-id="2">World</p>
    </div>
    """.trimIndent()
)

val lead = doc.selectFirst("p.lead")?.text()
val allParagraphs = doc.select("p")
val dataNode = doc.selectFirst("p[data-id=2]")?.text()
```

支持的常见查询方式包括：

- 标签：`div`
- id：`#root`
- class：`.lead`
- 属性：`[data-id=2]`
- 后代：`section article`
- 子级：`#news > article`

## XML 解析

```kotlin
import xyz.thewind.ksoup.Jsoup
import xyz.thewind.ksoup.parser.Parser

val xml = "<feed><entry id=\"1\">hello</entry></feed>"
val document = Jsoup.parse(xml, "", Parser.xmlParser())

println(document.selectFirst("entry")?.text())
```

也可以在网络请求时显式指定 XML 解析器：

```kotlin
val document = Jsoup.connect("https://example.com/feed.xml")
    .parser(Parser.xmlParser())
    .get()
```

## 清洗不可信 HTML

```kotlin
import xyz.thewind.ksoup.Jsoup
import xyz.thewind.ksoup.safety.Safelist

val dirty = "<p>Hello <script>alert(1)</script><a href=\"https://example.com\">link</a></p>"
val clean = Jsoup.clean(dirty, Safelist.basic())

println(clean)
```

校验内容是否满足 safelist：

```kotlin
val ok = Jsoup.isValid("<p><a href=\"https://example.com\">ok</a></p>", Safelist.relaxed())
```

## 网络抓取

Ksoup 提供了链式连接 API，风格接近 jsoup：

```kotlin
import xyz.thewind.ksoup.Connection
import xyz.thewind.ksoup.Jsoup

val document = Jsoup.connect("https://example.com/search")
    .userAgent("Ksoup/0.1")
    .referrer("https://example.com")
    .timeout(15_000)
    .data("q", "kmp")
    .get()

println(document.title())
```

### POST 表单

```kotlin
val response = Jsoup.connect("https://example.com/form")
    .method(Connection.Method.POST)
    .data("username", "demo")
    .data("password", "secret")
    .execute()

println(response.statusCode())
println(response.body())
```

### 发送原始请求体

```kotlin
val response = Jsoup.connect("https://example.com/api")
    .method(Connection.Method.POST)
    .header("Content-Type", "application/json")
    .requestBody("""{"name":"ksoup"}""")
    .execute()
```

### 文件上传

```kotlin
val response = Jsoup.connect("https://example.com/upload")
    .method(Connection.Method.POST)
    .data(
        key = "file",
        fileName = "hello.txt",
        input = "hello".encodeToByteArray(),
        contentType = "text/plain"
    )
    .execute()
```

### 会话与 Cookie

```kotlin
val session = Jsoup.newSession()

session.url("https://example.com/login").execute()
val home = session.newRequest("https://example.com/home").get()
```

### 代理与 TLS

```kotlin
val response = Jsoup.connect("https://example.com")
    .proxy("127.0.0.1", 8888)
    .validateTLSCertificates(false)
    .execute()
```

### 忽略 HTTP 状态错误或内容类型限制

```kotlin
val document = Jsoup.connect("https://example.com/data.json")
    .ignoreHttpErrors(true)
    .ignoreContentType(true)
    .get()
```

默认行为：

- HTTP 状态码大于等于 `400` 时会抛出 `HttpStatusException`
- 非 HTML/XML 内容类型在 `get()` / `post()` 解析时会抛出 `UnsupportedMimeTypeException`

## 本地发布到 Maven

仓库已经配置好了 `maven-publish`，可以直接发布到本地 Maven 仓库 `~/.m2/repository`。

### 发布核心库

```bash
./gradlew :ksoup:publishToMavenLocal
```

### 发布 Compose 示例共享模块

```bash
./gradlew :composeApp:publishToMavenLocal
```

### 发布全部可发布模块

```bash
./gradlew publishToMavenLocal
```

当前默认坐标：

- Group: `xyz.thewind.ksoup`
- Version: `0.1.0-SNAPSHOT`

核心库发布后可得到这些常用坐标：

- KMP 主坐标：`xyz.thewind.ksoup:ksoup:0.1.0-SNAPSHOT`
- JVM/Spring 坐标：`xyz.thewind.ksoup:ksoup-jvm:0.1.0-SNAPSHOT`
- Android 变体：`xyz.thewind.ksoup:ksoup-android:0.1.0-SNAPSHOT`

如果你要发正式版本，修改根项目 [build.gradle.kts](/Users/read/IdeaProjects/Ksoup/build.gradle.kts) 里的 `version` 后重新执行发布即可。

## 运行示例应用

### Android

```bash
./gradlew :androidApp:assembleDebug
```

### Desktop

```bash
./gradlew :composeApp:run
```

### iOS

用 Xcode 打开 [iosApp](/Users/read/IdeaProjects/Ksoup/iosApp) 并运行，或者先在 Gradle 中编译对应 framework。

## 当前实现说明

- HTML 解析、DOM、选择器、清洗逻辑都在 `commonMain`
- HTTP 传输层基于 Ktor
- 平台差异主要保留在 Ktor engine 这一层
  - Android/JVM: `CIO`
  - iOS: `Darwin`

这意味着业务层和解析逻辑可以保持平台无关，而网络底层仍使用每个平台最合适的实现。
