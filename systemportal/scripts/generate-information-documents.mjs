import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const projectDir = path.resolve(scriptDir, '..');
const sourceDir = path.resolve(projectDir, '..', '..', '..');
const outputFile = path.join(projectDir, 'src', 'content', 'informationDocuments.js');

const sources = {
  privacy: {
    zh: '用户隐私声明-中文.md',
    en: 'IDATAG Privacy Policy-English.md'
  },
  terms: {
    zh: '服务使用协议-中文.md',
    en: 'Terms of Service-English.md'
  },
  about: {
    zh: '关于安迪泰科技-中文.md',
    en: 'About iDatag Technology-English.md'
  }
};

function escapeHtml(value) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function convertMarkdown(source, filename) {
  const visibleLines = [];
  const html = [];
  let paragraph = [];

  const appendParagraph = () => {
    if (!paragraph.length) return;
    visibleLines.push(...paragraph);
    html.push(`<p>${paragraph.map(escapeHtml).join('\n')}</p>`);
    paragraph = [];
  };

  source.replace(/^\uFEFF/, '').split(/\r?\n/).forEach((line) => {
    const heading = line.match(/^(#{1,6})\s+(.*)$/);
    if (heading) {
      appendParagraph();
      const level = heading[1].length;
      visibleLines.push(heading[2]);
      html.push(`<h${level}>${escapeHtml(heading[2])}</h${level}>`);
    } else if (line.length) {
      paragraph.push(line);
    } else {
      appendParagraph();
    }
  });
  appendParagraph();

  const expectedLines = source
    .replace(/^\uFEFF/, '')
    .split(/\r?\n/)
    .filter((line) => line.length)
    .map((line) => line.replace(/^#{1,6}\s+/, ''));

  if (JSON.stringify(visibleLines) !== JSON.stringify(expectedLines)) {
    throw new Error(`${filename} 转换校验失败：生成内容与 Markdown 原文不一致。`);
  }

  return {
    html: html.join('\n'),
    sourceFile: filename,
    contentHash: crypto.createHash('sha256').update(JSON.stringify(visibleLines)).digest('hex'),
    lineCount: visibleLines.length
  };
}

const documents = {};
for (const [page, languages] of Object.entries(sources)) {
  documents[page] = {};
  for (const [language, filename] of Object.entries(languages)) {
    const source = fs.readFileSync(path.join(sourceDir, filename), 'utf8');
    documents[page][language] = convertMarkdown(source, filename);
  }
}

const generated = `// 此文件由 scripts/generate-information-documents.mjs 根据原始 Markdown 自动生成，请勿手工修改。\nexport default ${JSON.stringify(documents, null, 2)};\n`;
fs.mkdirSync(path.dirname(outputFile), { recursive: true });
fs.writeFileSync(outputFile, generated, 'utf8');

for (const [page, languages] of Object.entries(documents)) {
  for (const [language, document] of Object.entries(languages)) {
    console.log(`${page}/${language}: ${document.lineCount} lines, sha256 ${document.contentHash}`);
  }
}
