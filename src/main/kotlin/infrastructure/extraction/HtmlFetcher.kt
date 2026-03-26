package com.tenmilelabs.infrastructure.extraction

import com.tenmilelabs.domain.exception.FetchFailedException
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.SocketTimeoutException

class HtmlFetcher {

    companion object {
        private const val TIMEOUT_MS = 10_000
        private const val MAX_BODY_SIZE = 5 * 1024 * 1024 // 5 MB
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }

    fun fetch(url: String): Document {
        try {
            return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .followRedirects(true)
                .header("Accept-Encoding", "gzip, deflate")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .maxBodySize(MAX_BODY_SIZE)
                .get()
        } catch (e: HttpStatusException) {
            throw FetchFailedException(url, "HTTP ${e.statusCode} from $url", e)
        } catch (e: SocketTimeoutException) {
            throw FetchFailedException(url, "Timeout fetching $url", e)
        } catch (e: Exception) {
            throw FetchFailedException(url, "Failed to fetch $url: ${e.message}", e)
        }
    }
}
