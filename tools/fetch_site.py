#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""抓取 0xlei.tech/words 站点的前端壳与词汇核心数据（不含插画/音频媒体）。

创建者: zhenghq
用法: python3 -I fetch_site.py <输出目录>
"""
import os
import re
import sys
import urllib.request
import urllib.error

BASE = "https://0xlei.tech/words/"
UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"


def fetch(path):
    """下载单个路径，返回 bytes；失败返回 None。

    @param path: 相对 BASE 的路径
    @return: 响应字节或 None
    """
    url = BASE + path
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            if resp.status != 200:
                return None
            return resp.read()
    except Exception as exc:  # noqa: BLE001
        print(f"  [FAIL] {path}: {exc}")
        return None


def save(root, path, data):
    """把字节写入输出目录，自动创建父目录。

    @param root: 输出根目录
    @param path: 相对路径
    @param data: 文件字节
    """
    dest = os.path.join(root, path)
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, "wb") as fh:
        fh.write(data)
    print(f"  [OK] {path} ({len(data)} B)")


def extract_imports(text):
    """从 JS 文本中提取相对路径的模块名（./Xxx-hash.js）与样式文件。

    @param text: JS 源码
    @return: 模块文件名集合
    """
    names = set(re.findall(r'\./([A-Za-z0-9_.-]+\.js)', text))
    names |= set(re.findall(r'\./([A-Za-z0-9_.-]+\.css)', text))
    names |= set(re.findall(r'"assets/([A-Za-z0-9_.-]+\.css)"', text))
    return names


def main():
    """主流程：抓取 HTML、样式、主包，递归抓取全部动态 chunk 与核心数据。

    @return: 进程退出码
    """
    root = sys.argv[1]
    os.makedirs(root, exist_ok=True)

    html = fetch("")
    if not html:
        print("首页抓取失败")
        return 1
    save(root, "index.html", html)
    html_text = html.decode("utf-8", "ignore")

    assets = set(re.findall(r'/words/assets/([A-Za-z0-9_.-]+\.(?:js|css))', html_text))
    assets |= set(re.findall(r'assets/([A-Za-z0-9_.-]+\.(?:js|css))', html_text))
    for extra in ("favicon.svg", "icon-512.png"):
        assets.add(extra)

    pending = list(sorted(assets))
    done = set()
    while pending:
        name = pending.pop(0)
        if name in done:
            continue
        done.add(name)
        data = fetch("assets/" + name) if name.endswith((".js", ".css")) else fetch(name)
        if data is None:
            continue
        save(root, "assets/" + name if name.endswith((".js", ".css")) else name, data)
        if name.endswith(".js"):
            for dep in extract_imports(data.decode("utf-8", "ignore")):
                if dep not in done:
                    pending.append(dep)
            # 依赖可能是 assets/ 下的，也可能同级；统一按 assets/ 处理
            for dep in re.findall(r'assets/([A-Za-z0-9_.-]+\.js)', data.decode("utf-8", "ignore")):
                if dep not in done:
                    pending.append(dep)

    core = ["atlas/index.json", "atlas/classic/index.json"]
    for idx in range(0, 73):
        core.append(f"atlas/entries/{idx}.json")

    for path in core:
        data = fetch(path)
        if data is None:
            return 2
        save(root, path, data)

    print("完成")
    return 0


if __name__ == "__main__":
    sys.exit(main())
