#!/usr/bin/env python3
"""Renders docs/privacy-policy.html from the same strings the app shows (tools/strings_source.py),
so the published policy and the in-app policy can never disagree. English and Kannada in one page,
no scripts, no web fonts and no external requests."""
import os, sys
from html import escape

sys.path.insert(0, os.path.dirname(__file__))
from strings_source import EN, KN, PRIVACY_SECTIONS, PRIVACY_CONTACT, PRIVACY_UPDATED  # noqa: E402

OUT = os.path.join(os.path.dirname(__file__), "..", "docs", "privacy-policy.html")


def body(text, contact=False):
    out, items = [], []
    for line in [l for l in text.split("\n") if l.strip()]:
        if line.startswith("• "):
            items.append(f"<li>{escape(line[2:])}</li>")
            continue
        if items:
            out.append("<ul>" + "".join(items) + "</ul>"); items = []
        out.append(f"<p>{escape(line)}</p>")
    if items:
        out.append("<ul>" + "".join(items) + "</ul>")
    return "\n".join(out)


def article(t, lang):
    parts = [
        f'<article lang="{lang}" class="policy {lang}">',
        f'<p class="eyebrow">{escape(t["pp_updated"].replace("%1$s", PRIVACY_UPDATED[lang]))}</p>',
        f'<h1>{escape(t["privacy_policy"])}</h1>',
        f'<p class="intro">{escape(t["pp_intro"])}</p>',
    ]
    for key in PRIVACY_SECTIONS:
        cls = ' class="glance"' if key == "summary" else ""
        parts.append(f'<section{cls}><h2>{escape(t["pp_h_" + key])}</h2>\n{body(t["pp_b_" + key])}</section>')
    mail = f'<a href="mailto:{PRIVACY_CONTACT}">{PRIVACY_CONTACT}</a>'
    contact = escape(t["pp_b_contact"]).replace("%1$s", mail)
    parts.append(f'<section><h2>{escape(t["pp_h_contact"])}</h2><p>{contact}</p></section>')
    parts.append(f'<footer>{escape(t["privacy_summary"])}</footer>')
    parts.append("</article>")
    return "\n".join(parts)


CSS = """
:root{color-scheme:light;--bg:#F7F3EA;--surface:#FFFFFF;--text:#16203A;--muted:#5B6479;--line:#E4DDCD;--accent:#8A6414;--brand:#0D1729;--on-brand:#F4EFE5;--gold:#F3C96A}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){color-scheme:dark;--bg:#0D1729;--surface:#15233D;--text:#F4EFE5;--muted:#AAB4C7;--line:#24365A;--accent:#F3C96A;--brand:#1B2B48}}
:root[data-theme="dark"]{color-scheme:dark;--bg:#0D1729;--surface:#15233D;--text:#F4EFE5;--muted:#AAB4C7;--line:#24365A;--accent:#F3C96A;--brand:#1B2B48}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--text);font:16px/1.6 system-ui,-apple-system,"Segoe UI",Roboto,"Noto Sans","Noto Sans Kannada",sans-serif}
main{max-width:720px;margin:0 auto;padding:24px 16px 64px}
header{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap;margin-bottom:24px}
.brand{font-family:Georgia,"Times New Roman",serif;font-size:20px;letter-spacing:.02em}
.brand b{color:var(--accent);font-weight:500}
.langs{display:flex;border:1px solid var(--line);border-radius:999px;overflow:hidden}
.langs label{padding:10px 16px;min-height:44px;display:flex;align-items:center;cursor:pointer;color:var(--muted);font-size:14px}
input[name=lang]{position:absolute;opacity:0;pointer-events:none}
#l-en:checked~header label[for=l-en],#l-kn:checked~header label[for=l-kn]{background:var(--accent);color:var(--bg);font-weight:600}
#l-en:focus-visible~header label[for=l-en],#l-kn:focus-visible~header label[for=l-kn]{outline:2px solid var(--accent);outline-offset:2px}
#l-en:checked~.kn,#l-kn:checked~.en{display:none}
.eyebrow{text-transform:uppercase;letter-spacing:.12em;font-size:12px;color:var(--accent);margin:0 0 8px}
h1{font-family:Georgia,"Times New Roman",serif;font-weight:500;font-size:34px;line-height:1.2;margin:0 0 16px}
h2{font-family:Georgia,"Times New Roman",serif;font-weight:500;font-size:22px;line-height:1.3;margin:0 0 8px}
.intro{font-size:18px;color:var(--text)}
section{padding:20px 0;border-top:1px solid var(--line)}
section.glance{background:var(--brand);color:var(--on-brand);border:0;border-radius:20px;padding:20px 24px;margin:24px 0}
section.glance li::marker{color:var(--gold)}
p{margin:0 0 10px}
ul{margin:0 0 10px;padding-left:22px}
li{margin:6px 0}
li::marker{color:var(--accent)}
a{color:var(--accent)}
a:focus-visible,.langs label:focus-visible{outline:2px solid var(--accent);outline-offset:2px}
footer{margin-top:24px;padding-top:16px;border-top:1px solid var(--line);text-align:center;color:var(--muted);font-size:14px}
"""


def main():
    html = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<meta name="description" content="How Kairos Era handles your data: everything stays on your phone.">
<title>Kairos Era Privacy Policy</title>
<style>{CSS}</style>
</head>
<body>
<main>
<input type="radio" name="lang" id="l-en" checked>
<input type="radio" name="lang" id="l-kn">
<header>
<div class="brand">KAIROS <b>ERA</b></div>
<nav class="langs" aria-label="Language"><label for="l-en">English</label><label for="l-kn" lang="kn">ಕನ್ನಡ</label></nav>
</header>
{article(EN, "en")}
{article(KN, "kn")}
</main>
</body>
</html>
"""
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(html)
    print("wrote", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
