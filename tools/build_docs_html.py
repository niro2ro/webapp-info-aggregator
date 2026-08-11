#!/usr/bin/env python3
"""設計成果物の .md を、目次サイドバー＋テーマ対応＋Mermaid描画つき .html へ一括変換する。

- 正本は .md。HTML は閲覧用の併産物（CLAUDE.md §3「成果物のフォーマット規約」）。
- .md を編集したら本スクリプトを実行し、.md と .html を同一コミットに含める。
- 依存: python-markdown（`pip install markdown`）。

使い方:
    python3 tools/build_docs_html.py            # TARGETS を一括変換
    python3 tools/build_docs_html.py <a.md> ...  # 指定ファイルのみ変換
"""
import re, sys, markdown, pathlib, html as _html

# リポジトリルート = このスクリプト(tools/)の親
ROOT = pathlib.Path(__file__).resolve().parent.parent

# 既定の変換対象（リポジトリルートからの相対パス）
TARGETS = [
    "01_Requirements/要件定義書.md",
    "01_Requirements/未決事項回答ログ.md",
    "02_BasicDesign/ER図.md",
    "02_BasicDesign/テーブル定義書.md",
    "02_BasicDesign/要件トレース表.md",
    "02_BasicDesign/画面設計書.md",
    "02_BasicDesign/画面遷移図.md",
    "02_BasicDesign/バッチ設計書.md",
    "02_BasicDesign/外部IF設計書.md",
    "02_BasicDesign/メッセージ・エラー一覧.md",
    "03_DetailedDesign/アーキテクチャ設計書.md",
    "03_DetailedDesign/クラス設計書.md",
    "03_DetailedDesign/シーケンス設計書.md",
    "03_DetailedDesign/DI設計書.md",
    "03_DetailedDesign/例外・リトライ設計書.md",
    "03_DetailedDesign/データアクセス設計書.md",
    "03_DetailedDesign/設定・秘密情報設計書.md",
    "03_DetailedDesign/設計トレース表.md",
]

# タブ切り替え＋全幅レイアウトで出力するファイル（図を大きく見せたいもの）。
# H2 セクションごとにタブ化し、1画面に1セクションを全幅表示する。
TABBED = {
    "02_BasicDesign/画面遷移図.md",
}

STYLE = r"""
:root{
  --bg:#f6f7f9; --panel:#ffffff; --ink:#1c2230; --muted:#5c6579;
  --line:#e3e7ee; --line2:#eef1f6; --accent:#2f5bd6; --accent-weak:#eaf0ff;
  --chip:#f0f2f6; --shadow:0 1px 3px rgba(20,30,60,.06),0 8px 24px rgba(20,30,60,.05);
  --thead:#f0f3f8; --quote:#f7f9fc; --quote-bar:#c3ccdb;
}
@media (prefers-color-scheme:dark){
  :root:not([data-theme="light"]){
    --bg:#0f131a; --panel:#161c26; --ink:#e7ebf3; --muted:#9aa4b6;
    --line:#28313f; --line2:#1e2530; --accent:#6d92ff; --accent-weak:#1b2740;
    --chip:#212a37; --shadow:0 1px 3px rgba(0,0,0,.3),0 10px 30px rgba(0,0,0,.35);
    --thead:#202937; --quote:#1a212c; --quote-bar:#31465f;
  }
}
:root[data-theme="dark"]{
  --bg:#0f131a; --panel:#161c26; --ink:#e7ebf3; --muted:#9aa4b6;
  --line:#28313f; --line2:#1e2530; --accent:#6d92ff; --accent-weak:#1b2740;
  --chip:#212a37; --shadow:0 1px 3px rgba(0,0,0,.3),0 10px 30px rgba(0,0,0,.35);
  --thead:#202937; --quote:#1a212c; --quote-bar:#31465f;
}
*{box-sizing:border-box}
html{scroll-behavior:smooth}
body{margin:0;background:var(--bg);color:var(--ink);
  font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","Hiragino Kaku Gothic ProN","Noto Sans JP",Meiryo,sans-serif;
  line-height:1.75;-webkit-font-smoothing:antialiased;}
.layout{display:grid;grid-template-columns:260px minmax(0,1fr);gap:0;max-width:1180px;margin:0 auto;}
nav.toc-side{position:sticky;top:0;align-self:start;height:100vh;overflow-y:auto;
  padding:22px 14px 40px;border-right:1px solid var(--line);background:var(--panel);}
nav.toc-side .toc-title{font-size:12px;letter-spacing:.12em;text-transform:uppercase;color:var(--muted);
  font-weight:700;margin:0 8px 10px;}
nav.toc-side ul{list-style:none;margin:0;padding:0;}
nav.toc-side li{margin:0;}
nav.toc-side a{display:block;padding:5px 8px;border-radius:7px;color:var(--muted);text-decoration:none;
  font-size:13px;line-height:1.4;border-left:2px solid transparent;}
nav.toc-side a:hover{background:var(--line2);color:var(--ink);}
nav.toc-side > .toc > ul > li > a{color:var(--ink);font-weight:600;margin-top:4px;}
nav.toc-side ul ul{margin-left:10px;border-left:1px solid var(--line2);padding-left:4px;}
nav.toc-side ul ul a{font-size:12.5px;}
main{padding:34px 40px 100px;min-width:0;}
main h1{font-size:30px;line-height:1.3;margin:.1em 0 .5em;letter-spacing:.01em;}
main h2{font-size:22px;margin:1.8em 0 .5em;padding-bottom:.28em;border-bottom:2px solid var(--line);scroll-margin-top:16px;}
main h3{font-size:17px;margin:1.5em 0 .4em;color:var(--ink);scroll-margin-top:16px;}
main h4{font-size:14.5px;margin:1.2em 0 .3em;color:var(--muted);letter-spacing:.02em;}
main p{margin:.6em 0;}
main a{color:var(--accent);text-decoration:none;}
main a:hover{text-decoration:underline;}
main hr{border:0;border-top:1px solid var(--line);margin:2em 0;}
main ul,main ol{padding-left:1.4em;margin:.5em 0;}
main li{margin:.25em 0;}
code{background:var(--chip);padding:.08em .42em;border-radius:5px;font-size:.86em;
  font-family:"SF Mono",Menlo,Consolas,"Courier New",monospace;}
pre{background:var(--chip);border:1px solid var(--line);border-radius:10px;padding:14px 16px;overflow-x:auto;}
pre code{background:none;padding:0;font-size:12.5px;line-height:1.6;}
pre.mermaid{background:var(--panel);text-align:center;line-height:1.2;}
blockquote{margin:.8em 0;padding:10px 16px;background:var(--quote);border-left:4px solid var(--quote-bar);
  border-radius:0 8px 8px 0;color:var(--ink);}
blockquote p{margin:.3em 0;font-size:13.8px;}
.tbl-scroll{overflow-x:auto;margin:.8em 0;border:1px solid var(--line);border-radius:10px;box-shadow:var(--shadow);}
table{border-collapse:collapse;width:100%;font-size:13px;min-width:520px;background:var(--panel);}
th,td{text-align:left;vertical-align:top;padding:9px 12px;border-bottom:1px solid var(--line2);}
thead th{background:var(--thead);color:var(--ink);font-weight:700;font-size:11.5px;letter-spacing:.03em;
  white-space:nowrap;position:sticky;top:0;}
tbody tr:last-child td{border-bottom:0;}
tbody tr:hover{background:var(--line2);}
strong{font-weight:700;}
@media (max-width:820px){
  .layout{grid-template-columns:1fr;}
  nav.toc-side{position:static;height:auto;border-right:0;border-bottom:1px solid var(--line);}
  main{padding:24px 18px 80px;}
  main h1{font-size:25px;}
}
.doc-foot{margin-top:40px;padding-top:16px;border-top:1px solid var(--line);color:var(--muted);font-size:12px;}
.doc-nav{margin:0 0 18px;font-size:12.5px;color:var(--muted);}
.doc-nav a{color:var(--accent);}
"""

MERMAID_JS = r"""
<script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
<script>
  (function(){
    if (!window.mermaid) return;
    var dark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    mermaid.initialize({ startOnLoad: true, theme: dark ? 'dark' : 'default', securityLevel: 'strict', flowchart:{useMaxWidth:true}, er:{useMaxWidth:true} });
  })();
</script>
"""

# タブ表示（全幅・大きい図）用の追加スタイル
TAB_STYLE = r"""
.tabwrap{max-width:1500px;margin:0 auto;}
main.tabmain{padding:26px 34px 90px;min-width:0;}
main.tabmain h1{font-size:27px;margin:.1em 0 .5em;}
.tabbar{display:flex;flex-wrap:wrap;gap:6px;border-bottom:2px solid var(--line);margin:18px 0 22px;}
.tabbtn{background:transparent;border:0;color:var(--muted);font-family:inherit;font-size:14.5px;font-weight:600;
  padding:10px 18px;border-radius:10px 10px 0 0;border-bottom:3px solid transparent;margin-bottom:-2px;cursor:pointer;}
.tabbtn:hover{background:var(--line2);color:var(--ink);}
.tabbtn.active{color:var(--accent);border-bottom-color:var(--accent);background:var(--accent-weak);}
.tabpane{display:none;}
.tabpane.active{display:block;animation:fade .18s ease;}
@keyframes fade{from{opacity:.4}to{opacity:1}}
main.tabmain pre.mermaid{background:var(--panel);border:1px solid var(--line);border-radius:14px;box-shadow:var(--shadow);
  padding:24px;overflow:auto;text-align:center;line-height:1.2;min-height:64vh;display:flex;align-items:center;justify-content:center;}
main.tabmain pre.mermaid svg{max-width:100%;height:auto;}
@media (max-width:820px){ main.tabmain{padding:18px 14px 70px;} main.tabmain pre.mermaid{min-height:50vh;padding:12px;} }
"""

MERMAID_JS_BIG = r"""
<script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
<script>
  (function(){
    if (!window.mermaid) return;
    var dark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    mermaid.initialize({ startOnLoad: true, theme: dark ? 'dark' : 'default', securityLevel: 'strict',
      themeVariables:{ fontSize:'17px' },
      flowchart:{ useMaxWidth:true, htmlLabels:true, nodeSpacing:55, rankSpacing:78, padding:16 } });
  })();
</script>
<script>
  (function(){
    var btns = document.querySelectorAll('.tabbtn');
    var panes = document.querySelectorAll('.tabpane');
    btns.forEach(function(b){
      b.addEventListener('click', function(){
        btns.forEach(function(x){ x.classList.remove('active'); });
        panes.forEach(function(x){ x.classList.remove('active'); });
        b.classList.add('active');
        var p = document.getElementById('pane' + b.dataset.i);
        if (p) p.classList.add('active');
        window.scrollTo(0, 0);
      });
    });
  })();
</script>
"""


def build_tabbed(title, src_rel, body):
    """H2 セクションごとにタブ化した全幅ページを組み立てる。図タブを既定表示にする。"""
    # 最初の <h2 で分割。先頭要素はイントロ（H1＋メタ＋前書き）
    parts = re.split(r'(?=<h2\b)', body)
    intro = parts[0]
    sections = parts[1:]
    tabs, panes = [], []
    for i, sec in enumerate(sections):
        mlab = re.search(r'<h2[^>]*>(.*?)</h2>', sec, flags=re.DOTALL)
        label = re.sub(r'<[^>]+>', '', mlab.group(1)).strip() if mlab else f'{i+1}'
        # パネル内の見出し（タブと重複）を除去
        content = re.sub(r'^\s*<h2[^>]*>.*?</h2>', '', sec, count=1, flags=re.DOTALL)
        active = ' active' if i == 0 else ''
        tabs.append(f'<button class="tabbtn{active}" data-i="{i}">{_html.escape(label)}</button>')
        panes.append(f'<div class="tabpane{active}" id="pane{i}">{content}</div>')
    return f"""<!doctype html>
<html lang="ja">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{_html.escape(title)}</title>
<style>{STYLE}{TAB_STYLE}</style>
</head>
<body>
<div class="tabwrap">
  <main class="tabmain">
    <div class="doc-nav">📁 {_html.escape(src_rel)}（正本は同名 .md）／タブで切替・図は全幅表示</div>
    {intro}
    <div class="tabbar">{''.join(tabs)}</div>
    {''.join(panes)}
    <div class="doc-foot">テーマ別最新情報アグリゲーター — HTML整形版（正本は <code>{_html.escape(src_rel)}</code>）</div>
  </main>
</div>
{MERMAID_JS_BIG}
</body>
</html>
"""


def convert(src_rel):
    src = (ROOT / src_rel)
    out = src.with_suffix(".html")
    text = src.read_text(encoding="utf-8")

    # 1) Mermaid フェンスを退避（markdown 変換の干渉を避ける）
    mermaids = []

    def _stash(m):
        inner = m.group(1)
        esc = inner.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        mermaids.append(esc)
        return f"\n\nMERMAIDBLOCK{len(mermaids) - 1}ENDMERMAID\n\n"

    text = re.sub(r"```mermaid\n(.*?)```", _stash, text, flags=re.DOTALL)

    md = markdown.Markdown(extensions=["tables", "fenced_code", "toc", "attr_list", "sane_lists"],
                           extension_configs={"toc": {"toc_depth": "2-3", "permalink": False}})
    body = md.convert(text)
    toc = md.toc

    # 2) Mermaid プレースホルダを復元
    body = re.sub(r"<p>MERMAIDBLOCK(\d+)ENDMERMAID</p>",
                  lambda m: f'<pre class="mermaid">{mermaids[int(m.group(1))]}</pre>', body)

    # 3) テーブルを横スクロール枠で包む
    body = re.sub(r"<table>", '<div class="tbl-scroll"><table>', body)
    body = re.sub(r"</table>", "</table></div>", body)

    # タイトル = 最初の H1
    m = re.search(r"^#\s+(.+)$", text, flags=re.MULTILINE)
    title = re.sub(r"<[^>]+>", "", m.group(1).strip()) if m else src.stem

    has_mermaid = len(mermaids) > 0

    if src_rel in TABBED:
        page = build_tabbed(title, src_rel, body)
    else:
        page = f"""<!doctype html>
<html lang="ja">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{_html.escape(title)}</title>
<style>{STYLE}</style>
</head>
<body>
<div class="layout">
  <nav class="toc-side">
    <div class="toc-title">目次</div>
    {toc}
  </nav>
  <main>
    <div class="doc-nav">📁 {_html.escape(src_rel)}（正本は同名 .md）</div>
    {body}
    <div class="doc-foot">テーマ別最新情報アグリゲーター — HTML整形版（正本は <code>{_html.escape(src_rel)}</code>）</div>
  </main>
</div>
{MERMAID_JS if has_mermaid else ""}
</body>
</html>
"""
    out.write_text(page, encoding="utf-8")
    return out, len(page), has_mermaid


def main(argv):
    if len(argv) > 1:
        # 引数指定時: 絶対/相対どちらでも ROOT 相対に正規化
        targets = []
        for a in argv[1:]:
            p = pathlib.Path(a).resolve()
            try:
                targets.append(str(p.relative_to(ROOT)))
            except ValueError:
                targets.append(a)  # ROOT 外はそのまま
    else:
        targets = TARGETS
    for t in targets:
        out, n, mm = convert(t)
        print(f"wrote {out}  {n} bytes  mermaid={mm}")


if __name__ == "__main__":
    main(sys.argv)
