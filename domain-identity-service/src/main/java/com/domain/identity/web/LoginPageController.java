package com.domain.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.util.HtmlUtils;

/** Provides a Mongolian login form while Spring Security handles credentials and CSRF. */
@Controller
public class LoginPageController {
    /** Renders only a server-generated CSRF token and a fixed failure message. */
    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> login(final HttpServletRequest request) {
        final CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        final String message = request.getParameter("error") == null ? ""
                : "<p class=error role=alert>Имэйл эсвэл нууц үг буруу байна.</p>";
        final String html = """
                <!doctype html><html lang="mn"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1"><title>Нэвтрэх | Bemon</title>
                <style>*{box-sizing:border-box}body{margin:0;background:#f4f5ed;color:#19372e;font-family:Arial,sans-serif;
                min-height:100vh;display:grid;place-items:center;padding:24px}main{background:white;width:100%%;
                max-width:440px;border:1px solid #e2e8e1;border-radius:16px;padding:40px}h1{font-size:28px;letter-spacing:-1px;
                margin:30px 0 14px}.brand{font-size:30px;font-weight:bold}p{color:#64736d;font-size:13px;line-height:1.7}
                label{display:block;margin-top:24px;font-size:12px;font-weight:bold}input{display:block;width:100%%;
                margin-top:10px;padding:13px;border:1px solid #d9e0d8;border-radius:7px;font:14px Arial}
                button{width:100%%;padding:15px;border:0;border-radius:7px;background:#245944;color:white;font-weight:bold;
                margin-top:28px;cursor:pointer}input:focus,button:focus-visible{outline:2px solid #669a6c;outline-offset:3px}
                .error{color:#8e4534;background:#fff5f2;padding:13px;border-radius:7px}</style></head><body><main>
                <div class=brand>bemon.</div><h1>Тавтай морил.</h1><p>Бүртгэлтэй имэйл, нууц үгээ оруулна уу.</p>%s
                <form action="/login" method="post"><label for="username">Имэйл</label>
                <input id="username" name="username" type="email" autocomplete="username" required autofocus>
                <label for="password">Нууц үг</label><input id="password" name="password" type="password"
                autocomplete="current-password" required><input type="hidden" name="%s" value="%s">
                <button type="submit">Нэвтрэх →</button></form></main></body></html>
                """.formatted(message, HtmlUtils.htmlEscape(csrf.getParameterName()), HtmlUtils.htmlEscape(csrf.getToken()));
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(html);
    }
}
