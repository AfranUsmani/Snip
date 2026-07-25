package io.github.afranusmani.urlshortener.controller;

import io.github.afranusmani.urlshortener.exception.ShortCodeNotFoundException;
import io.github.afranusmani.urlshortener.model.UrlMapping;
import io.github.afranusmani.urlshortener.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * A TinyURL-style safety "preview" page: instead of redirecting, it shows where a
 * short link goes so the visitor can decide before leaving. Server-rendered so it
 * works without JavaScript, and it never records a click (previewing isn't a visit).
 */
@RestController
@Tag(name = "Preview", description = "See where a short link goes before following it")
public class PreviewController {

    private final UrlService service;

    public PreviewController(UrlService service) {
        this.service = service;
    }

    @GetMapping(value = "/preview/{shortCode:[0-9A-Za-z_-]+}", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Preview a short link's destination without redirecting")
    public ResponseEntity<String> preview(@PathVariable String shortCode) {
        UrlMapping mapping;
        try {
            mapping = service.getMapping(shortCode);
        } catch (ShortCodeNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_HTML)
                    .body(page("Link not found",
                            "No short link exists for <code>/" + HtmlUtils.htmlEscape(shortCode) + "</code>.",
                            null));
        }

        String code = HtmlUtils.htmlEscape(mapping.getShortCode());
        if (mapping.isExpired()) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .contentType(MediaType.TEXT_HTML)
                    .body(page("Link expired",
                            "The short link <code>/" + code + "</code> has expired and no longer forwards anywhere.",
                            null));
        }

        String destination = HtmlUtils.htmlEscape(mapping.getOriginalUrl());
        String body = "You’re about to be taken from the short link <code>/" + code + "</code> to:"
                + "<div class=\"dest\">" + destination + "</div>";
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(page("Preview link", body, mapping.getOriginalUrl()));
    }

    /** Minimal self-contained page matching the dashboard's dark theme. */
    private static String page(String title, String bodyHtml, String continueHref) {
        String cta = continueHref == null ? ""
                : "<a class=\"btn\" href=\"" + HtmlUtils.htmlEscape(continueHref)
                    + "\" rel=\"noopener nofollow\">Continue to site ↗</a>";
        return """
                <!DOCTYPE html>
                <html lang="en"><head><meta charset="UTF-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                <meta name="robots" content="noindex"/>
                <title>%s · Snip</title>
                <style>
                  :root{--bg:#0f1115;--card:#1b202b;--border:#2a3142;--text:#e7ebf3;--muted:#98a2b8;--accent:#6d5efc;--accent2:#22d3ee}
                  *{box-sizing:border-box}
                  body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px;
                    font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;color:var(--text);
                    background:radial-gradient(1000px 500px at 80%% -10%%,rgba(109,94,252,.18),transparent 60%%),
                      radial-gradient(900px 450px at -10%% 10%%,rgba(34,211,238,.12),transparent 55%%),var(--bg)}
                  .card{background:var(--card);border:1px solid var(--border);border-radius:16px;max-width:520px;width:100%%;
                    padding:32px;box-shadow:0 10px 30px rgba(0,0,0,.35);text-align:center}
                  .logo{font-size:2rem}
                  h1{font-size:1.4rem;margin:10px 0 6px;
                    background:linear-gradient(135deg,#6d5efc,#22d3ee);-webkit-background-clip:text;background-clip:text;color:transparent}
                  p{color:var(--muted);line-height:1.6}
                  code{font-family:ui-monospace,Menlo,Consolas,monospace;color:var(--accent2)}
                  .dest{word-break:break-all;background:#12161f;border:1px solid var(--border);border-radius:10px;
                    padding:12px 14px;margin:16px 0 20px;font-family:ui-monospace,Menlo,Consolas,monospace;color:var(--text)}
                  .btn{display:inline-block;background:linear-gradient(135deg,#6d5efc,#22d3ee);color:#fff;text-decoration:none;
                    font-weight:600;padding:12px 24px;border-radius:10px}
                  .btn:hover{filter:brightness(1.08)}
                  .foot{margin-top:22px;font-size:.8rem}
                  a.home{color:var(--muted)}
                </style></head>
                <body><div class="card">
                  <div class="logo">🔗</div>
                  <h1>%s</h1>
                  <p>%s</p>
                  %s
                  <div class="foot"><a class="home" href="/">← Back to Snip</a></div>
                </div></body></html>
                """.formatted(
                HtmlUtils.htmlEscape(title), HtmlUtils.htmlEscape(title), bodyHtml, cta);
    }
}
