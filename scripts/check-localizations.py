#!/usr/bin/env python3
"""Check string coverage and formatting contracts without an Android runtime."""
from collections import Counter
from pathlib import Path
import re
import xml.etree.ElementTree as ET

res = Path(__file__).resolve().parents[1] / 'app/src/main/res'
base = {e.attrib['name']: e for e in ET.parse(res / 'values/strings.xml').getroot()
        if e.get('translatable') != 'false'}

def placeholders(text):
    return Counter(re.findall(r'%(?:\d+\$)?[sd]', text or ''))

for lang in ('es', 'pt', 'fr', 'ar', 'fa'):
    translated = {e.attrib['name']: e for e in ET.parse(res / f'values-{lang}/strings.xml').getroot()}
    assert translated.keys() == base.keys(), (lang, base.keys() ^ translated.keys())
    for key, original in base.items():
        target = translated[key]
        assert target.tag == original.tag, (lang, key)
        expected = placeholders(original.text if original.tag == 'string' else original.find("item[@quantity='other']").text)
        for item in ([target] if target.tag == 'string' else target):
            assert item.text and placeholders(item.text) == expected, (lang, key, item.text)
        if target.tag == 'plurals':
            required = {'zero', 'one', 'two', 'few', 'many', 'other'} if lang == 'ar' else {'one', 'other'}
            assert required <= {item.get('quantity') for item in target}, (lang, key)
    print(f'{lang}: {len(translated)} resources; coverage, placeholders, and plural categories OK')
