package com.kevin.ink.epub

import java.io.File

/**
 * Inyecta nuestros estilos y scripts en el HTML de un capítulo:
 * temas (día/sepia/noche), márgenes cómodos, imágenes ajustadas y
 * guardado de la posición de lectura. El tamaño de letra se controla
 * aparte con WebSettings.textZoom.
 */
object ChapterRenderer {

    /** El WebView resuelve imágenes/CSS relativos contra esta URL base. */
    fun baseUrlFor(chapterFile: File): String =
        "file://${chapterFile.parentFile?.absolutePath}/"

    fun render(rawHtml: String, theme: String): String {
        val style = "<style id=\"ink-style\">$CSS</style>"
        val script = "<script>${initScript(theme)}\n$SCROLL_SCRIPT</script>"

        var doc = rawHtml
        doc = if (doc.contains("</head>", ignoreCase = true)) {
            doc.replaceFirst(Regex("</head>", RegexOption.IGNORE_CASE), "$style</head>")
        } else {
            style + doc
        }
        doc = if (doc.contains("</body>", ignoreCase = true)) {
            doc.replaceFirst(Regex("</body>", RegexOption.IGNORE_CASE), "$script</body>")
        } else {
            doc + script
        }
        return doc
    }

    private fun initScript(theme: String): String = """
        (function(){
          var h=document.documentElement;
          h.classList.remove('ink-day','ink-sepia','ink-night');
          h.classList.add('ink-$theme');
          if(!document.querySelector('meta[name=viewport]')){
            var m=document.createElement('meta');
            m.name='viewport';
            m.content='width=device-width, initial-scale=1';
            (document.head||document.documentElement).appendChild(m);
          }
        })();
    """.trimIndent()

    private val SCROLL_SCRIPT = """
        (function(){
          var t=null;
          window.addEventListener('scroll', function(){
            if(t) return;
            t=setTimeout(function(){
              t=null;
              var h=document.documentElement;
              var max=h.scrollHeight-h.clientHeight;
              var f=max>0?(window.scrollY/max):0;
              if(window.InkBridge && InkBridge.saveScroll){ InkBridge.saveScroll(f); }
            }, 400);
          }, {passive:true});
        })();
    """.trimIndent()

    private val CSS = """
        html { -webkit-text-size-adjust: 100%; }
        body {
          margin: 0 auto !important;
          padding: 28px 22px 80px 22px !important;
          max-width: 42em !important;
          line-height: 1.65 !important;
          word-wrap: break-word;
          overflow-wrap: break-word;
          -webkit-hyphens: auto; hyphens: auto;
        }
        img, svg, video, table { max-width: 100% !important; height: auto !important; }
        a { color: #4a90d9 !important; text-decoration: none; }
        html.ink-day, html.ink-day body { background:#ffffff !important; color:#1b1b1b !important; }
        html.ink-sepia, html.ink-sepia body { background:#f4ecd8 !important; color:#5b4636 !important; }
        html.ink-night, html.ink-night body { background:#121212 !important; color:#c9c9c9 !important; }
        html.ink-night img { filter: brightness(.82) !important; }
        html.ink-night a { color:#6aa9e0 !important; }

        /* Tipografía del modo líquido (PDF reflowado) — lectura cómoda estilo libro */
        body.ink-liquid {
          font-family: Georgia, "Noto Serif", serif !important;
          line-height: 1.8 !important;
          padding: 34px 26px 96px !important;
        }
        .ink-liquid p { margin: 0 0 1.05em 0 !important; text-align: left; }
        .ink-liquid h2 { font-size: 1.45em; font-weight: 700; line-height: 1.25; margin: 1.7em 0 0.55em; }
        .ink-liquid h3 { font-size: 1.15em; font-weight: 700; line-height: 1.3; margin: 1.35em 0 0.4em; }
        .ink-liquid h2:first-child, .ink-liquid h3:first-child { margin-top: 0; }
    """.trimIndent()
}
