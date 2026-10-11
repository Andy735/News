
package com.example.service

import android.os.Bundle
import android.text.Html
import android.util.Xml
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.GET
import java.text.SimpleDateFormat
import java.util.Locale

private val Navy = Color(0xFF12243A)
private val Blue = Color(0xFF244D83)
private val Page = Color(0xFFF2F5FA)
private val Muted = Color(0xFF64748B)

data class NewsArticle(
    val id: String,
    val title: String,
    val summary: String,
    val source: String,
    val publishedAt: String,
    val url: String
)

interface NewsApi {
    @GET("rss?hl=zh-TW&gl=TW&ceid=TW:zh-Hant")
    suspend fun getNews(): ResponseBody
}

object ApiClient {
    val newsApi: NewsApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://news.google.com/")
            .build()
            .create(NewsApi::class.java)
    }
}

fun parseNewsRss(xml: String): List<NewsArticle> {
    val parser = Xml.newPullParser()
    parser.setInput(xml.reader())

    val articles = mutableListOf<NewsArticle>()
    var eventType = parser.eventType
    var insideItem = false
    var currentTag = ""
    var title = ""
    var link = ""
    var summary = ""
    var source = "Google News"
    var publishedAt = ""

    while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
        when (eventType) {
            org.xmlpull.v1.XmlPullParser.START_TAG -> {
                currentTag = parser.name ?: ""
                if (currentTag == "item") {
                    insideItem = true
                    title = ""
                    link = ""
                    summary = ""
                    source = "Google News"
                    publishedAt = ""
                }
            }

            org.xmlpull.v1.XmlPullParser.TEXT -> {
                if (insideItem) {
                    val text = parser.text.orEmpty().trim()
                    if (text.isNotBlank()) {
                        when (currentTag) {
                            "title" -> title += text
                            "link" -> link += text
                            "description" -> summary += text
                            "source" -> source = text
                            "pubDate" -> publishedAt = text
                        }
                    }
                }
            }

            org.xmlpull.v1.XmlPullParser.END_TAG -> {
                if (parser.name == "item") {
                    val cleanSummary = Html.fromHtml(
                        summary,
                        Html.FROM_HTML_MODE_LEGACY
                    ).toString()
                        .replace(Regex("\\s+"), " ")
                        .trim()

                    articles += NewsArticle(
                        id = link.ifBlank { title },
                        title = title,
                        summary = cleanSummary.ifBlank { "點選閱讀完整新聞內容。" },
                        source = source,
                        publishedAt = publishedAt.toDisplayDate(),
                        url = link
                    )
                    insideItem = false
                }
                currentTag = ""
            }
        }
        eventType = parser.next()
    }

    return articles.filter { it.title.isNotBlank() && it.url.isNotBlank() }
}

fun String.toDisplayDate(): String {
    if (isBlank()) return "最新"

    return runCatching {
        val input = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
        val output = SimpleDateFormat("MM/dd HH:mm", Locale.TAIWAN)
        output.format(input.parse(this) ?: return "最新")
    }.getOrDefault("最新")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                NewsScreen()
            }
        }
    }
}

@Composable
fun NewsScreen() {
    var articles by remember {
        mutableStateOf<List<NewsArticle>>(emptyList())
    }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<NewsArticle?>(null) }

    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    suspend fun loadArticles() {
        loading = true
        error = null

        try {
            articles = parseNewsRss(ApiClient.newsApi.getNews().string())
        } catch (e: Exception) {
            error = "無法取得新聞，請確認網路後重試。"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        loadArticles()
    }

    val filtered = articles.filter {
        it.title.contains(search, ignoreCase = true) ||
                it.summary.contains(search, ignoreCase = true) ||
                it.source.contains(search, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Page)
    ) {
        // 品牌標頭
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Navy,
                    RoundedCornerShape(
                        bottomStart = 24.dp,
                        bottomEnd = 24.dp
                    )
                )
                .padding(
                    start = 22.dp,
                    end = 22.dp,
                    top = 28.dp,
                    bottom = 24.dp
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "TAIWAN NEWS",
                        color = Color(0xFFA9C9F5),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.5.sp
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        "即時新聞",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = Color(0xFF27415D),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "RSS",
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 8.dp
                        ),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "掌握台灣與全球焦點，一次瀏覽最新繁體中文新聞。",
                color = Color(0xFFD2DDEA),
                fontSize = 14.sp
            )
        }

        // 搜尋欄
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .offset(y = (-14).dp),
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            shadowElevation = 3.dp
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 15.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⌕", color = Muted, fontSize = 24.sp)

                Spacer(Modifier.width(10.dp))

                Box(Modifier.weight(1f)) {
                    if (search.isEmpty()) {
                        Text(
                            "搜尋新聞標題、摘要或來源",
                            color = Muted,
                            fontSize = 14.sp
                        )
                    }

                    BasicTextField(
                        value = search,
                        onValueChange = { search = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Navy,
                            fontSize = 14.sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (search.isNotEmpty()) {
                    Text(
                        "清除",
                        color = Blue,
                        modifier = Modifier.clickable {
                            search = ""
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 22.dp,
                    end = 22.dp,
                    top = 0.dp,
                    bottom = 10.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (search.isBlank()) "今日焦點" else "搜尋結果",
                color = Navy,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "${filtered.size} 則新聞",
                color = Muted,
                fontSize = 12.sp
            )
        }

        when {
            loading && articles.isEmpty() -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Blue)
                        Spacer(Modifier.height(12.dp))
                        Text("正在載入新聞...", color = Muted)
                    }
                }
            }

            error != null && articles.isEmpty() -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error ?: "", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            scope.launch { loadArticles() }
                        }) {
                            Text("重新嘗試")
                        }
                    }
                }
            }

            filtered.isEmpty() -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("找不到符合條件的新聞", color = Muted)
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 18.dp,
                        end = 18.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 第一則新聞作為焦點卡片
                    if (search.isBlank()) {
                        item {
                            val featured = filtered.first()

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selected = featured },
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Blue
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(22.dp)
                                ) {
                                    Surface(
                                        color = Color(0xFF42699B),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            "焦點新聞",
                                            modifier = Modifier.padding(
                                                horizontal = 10.dp,
                                                vertical = 6.dp
                                            ),
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(Modifier.height(16.dp))

                                    Text(
                                        featured.title,
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        lineHeight = 30.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(Modifier.height(10.dp))

                                    Text(
                                        featured.summary,
                                        color = Color(0xFFE0EAF7),
                                        fontSize = 14.sp,
                                        lineHeight = 22.sp,
                                        maxLines = 3
                                    )

                                    Spacer(Modifier.height(16.dp))

                                    Text(
                                        "${featured.source} · ${featured.publishedAt}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            Text(
                                "最新新聞",
                                color = Navy,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(4.dp))
                        }
                    }

                    items(
                        items = if (search.isBlank()) {
                            filtered.drop(1)
                        } else {
                            filtered
                        },
                        key = { it.id }
                    ) { article ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = article },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 1.dp
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement =
                                        Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${article.source}  /  ${article.publishedAt}",
                                        color = Blue,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )

                                    Text(
                                        "閱讀全文  →",
                                        color = Muted,
                                        fontSize = 11.sp
                                    )
                                }

                                Spacer(Modifier.height(10.dp))

                                Text(
                                    article.title,
                                    color = Navy,
                                    fontSize = 16.sp,
                                    lineHeight = 23.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    article.summary,
                                    color = Muted,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    maxLines = 2
                                )
                            }
                        }
                    }

                    item {
                        OutlinedButton(
                            onClick = {
                                scope.launch { loadArticles() }
                            },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (loading) "更新中..." else "重新整理新聞"
                            )
                        }

                        error?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "更新失敗，顯示的可能是先前載入的資料。",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }

    selected?.let { article ->
        AlertDialog(
            onDismissRequest = { selected = null },
            containerColor = Color.White,
            title = {
                Text(
                    article.title,
                    color = Navy,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "${article.source} · ${article.publishedAt}\n\n${article.summary}",
                    color = Muted,
                    lineHeight = 24.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    selected = null
                    uriHandler.openUri(article.url)
                }) {
                    Text("開啟原文")
                }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }) {
                    Text("關閉")
                }
            }
        )
    }
}
