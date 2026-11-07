#!/usr/bin/env node
/**
 * 生成发布说明中的「本次发布的贡献者」章节。
 *
 * 输出结构：
 *   ### 🤝 本次发布的贡献者
 *   > 汇总（PR 数 / 提交数 / 贡献者数 + 对比范围）
 *   #### 📌 PR 一览        —— 本次发布的全部 PR 及其贡献者
 *   #### 👤 按贡献者查看   —— 每位贡献者的 PR 与提交记录
 *
 * 数据全部来自 GitHub REST API：
 *   1. 取上一次已发布 Release 的 tag 作为基线（可用 --base 覆盖）；
 *   2. compare(base...head) 得到本次发布包含的提交；
 *   3. 每个提交反查 /commits/{sha}/pulls 得到所属 PR。
 *
 * 生成失败不阻塞发版：脚本始终写出文件，失败时写一段降级提示，退出码仍为 0。
 *
 * 用法：
 *   GITHUB_TOKEN=xxx GITHUB_REPOSITORY=owner/repo \
 *   node .github/scripts/release-notes.mjs --out release-notes-contributors.md
 */

import { writeFileSync } from 'node:fs';

const API_BASE = 'https://api.github.com';

/** 自动化账号的提交不计入贡献者（避免机器人占满名单）。 */
const BOT_LOGINS = new Set([
  'github-actions',
  'actions-user',
  'web-flow',
  'dependabot',
  'renovate',
  'renovate-bot',
]);

/** 单条发布说明最多渲染的提交数，防止提交过多把 Release 正文撑爆。 */
const MAX_RENDERED_COMMITS = 200;

function parseArgs(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i++) {
    const token = argv[i];
    if (!token.startsWith('--')) continue;
    const name = token.slice(2);
    const next = argv[i + 1];
    if (next && !next.startsWith('--')) {
      args[name] = next;
      i++;
    } else {
      args[name] = 'true';
    }
  }
  return args;
}

const args = parseArgs(process.argv.slice(2));

const options = {
  repo: args.repo || process.env.GITHUB_REPOSITORY || '',
  token: process.env.GITHUB_TOKEN || '',
  head: args.head || process.env.RELEASE_HEAD_REF || 'main',
  base: args.base || process.env.RELEASE_BASE_REF || '',
  /** 本次正在发布的 tag：选基线时要跳过它，避免重跑时算出空区间。 */
  currentTag: args['current-tag'] || process.env.RELEASE_CURRENT_TAG || '',
  out:
    args.out ||
    process.env.RELEASE_NOTES_FILE ||
    'release-notes-contributors.md',
  /** 反查 PR 的提交上限（每个提交一次 API 调用），超出部分只列提交不列 PR。 */
  maxPrLookups: Number(
    args['max-pr-lookups'] || process.env.RELEASE_MAX_PR_LOOKUPS || 250,
  ),
};

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function request(path, { soft = false } = {}) {
  const url = `${API_BASE}${path}`;
  const headers = {
    accept: 'application/vnd.github+json',
    'user-agent': 'legado-release-notes',
    'x-github-api-version': '2022-11-28',
  };
  if (options.token) headers.authorization = `Bearer ${options.token}`;

  let lastError;
  for (let attempt = 0; attempt < 3; attempt++) {
    let res;
    try {
      res = await fetch(url, { headers });
    } catch (error) {
      lastError = error;
      await sleep(1000 * (attempt + 1));
      continue;
    }
    if (res.status === 404 && soft) return null;
    if (res.ok) return res.json();
    const message = `GitHub API ${res.status} ${res.statusText}: ${path}`;
    if (res.status === 403 || res.status === 429 || res.status >= 500) {
      lastError = new Error(message);
      await sleep(1500 * (attempt + 1));
      continue;
    }
    throw new Error(message);
  }
  throw lastError || new Error(`GitHub API 请求失败: ${path}`);
}

async function mapLimit(items, limit, worker) {
  const results = new Array(items.length);
  let cursor = 0;
  const size = Math.max(1, Math.min(limit, items.length));
  await Promise.all(
    Array.from({ length: size }, async () => {
      while (cursor < items.length) {
        const current = cursor++;
        results[current] = await worker(items[current], current);
      }
    }),
  );
  return results;
}

const normalizeTag = (tag) => String(tag || '').replace(/^beta-/, '');

/** 取上一次已发布的 Release 作为对比基线；没有 Release 时返回空串。 */
async function pickBaseTag() {
  const releases = await request(`/repos/${options.repo}/releases?per_page=30`);
  for (const release of releases || []) {
    if (release.draft) continue;
    if (
      options.currentTag &&
      normalizeTag(release.tag_name) === normalizeTag(options.currentTag)
    )
      continue;
    if (release.tag_name) return release.tag_name;
  }
  return '';
}

async function collectCommits(baseRef) {
  if (baseRef) {
    try {
      const data = await request(
        `/repos/${options.repo}/compare/${encodeURIComponent(baseRef)}...${encodeURIComponent(options.head)}`,
      );
      const commits = data.commits || [];
      return {
        baseRef,
        compareUrl: data.html_url || '',
        totalCommits: Number.isFinite(data.total_commits)
          ? data.total_commits
          : commits.length,
        commits,
      };
    } catch (error) {
      console.warn(
        `compare ${baseRef}...${options.head} 失败，回退到最近提交列表：${error.message}`,
      );
    }
  }
  const list = await request(
    `/repos/${options.repo}/commits?sha=${encodeURIComponent(options.head)}&per_page=100`,
  );
  return {
    baseRef: '',
    compareUrl: '',
    totalCommits: (list || []).length,
    commits: list || [],
  };
}

const isBotLogin = (login) => {
  const value = String(login || '').toLowerCase();
  return value.endsWith('[bot]') || BOT_LOGINS.has(value);
};

function toCommit(raw) {
  const message = raw.commit?.message || '';
  const subject = message.split('\n')[0].trim();
  const parents = raw.parents?.length ?? 0;
  return {
    sha: raw.sha || '',
    shortSha: (raw.sha || '').slice(0, 7),
    subject: subject || '(无提交说明)',
    message,
    url: raw.html_url || `https://github.com/${options.repo}/commit/${raw.sha}`,
    authorLogin: raw.author?.login || '',
    authorName: raw.commit?.author?.name || raw.author?.login || '未知贡献者',
    isMerge:
      parents > 1 ||
      /^Merge (pull request|branch|remote-tracking|tag)\b/i.test(subject),
  };
}

const isBotCommit = (commit) =>
  isBotLogin(commit.authorLogin) || isBotLogin(commit.authorName);

/** 从 Co-authored-by 尾注里补出协作贡献者（仅 noreply 邮箱能映射到 GitHub 账号）。 */
function parseCoAuthors(message, authorLogin) {
  const coAuthors = [];
  const pattern = /^co-authored-by:\s*(.+?)\s*<([^>]+)>\s*$/gim;
  let match;
  while ((match = pattern.exec(message)) !== null) {
    const name = match[1].trim();
    const email = match[2].trim();
    const loginMatch = /^(?:\d+\+)?([^@]+)@users\.noreply\.github\.com$/i.exec(
      email,
    );
    const login = loginMatch ? loginMatch[1] : '';
    if (
      login &&
      login.toLowerCase() === String(authorLogin || '').toLowerCase()
    )
      continue;
    if (!name && !login) continue;
    if (isBotLogin(login)) continue;
    coAuthors.push({
      login,
      name: name || login,
      url: login ? `https://github.com/${login}` : '',
    });
  }
  return coAuthors;
}

function ensureContributor(contributors, { login, name, url }) {
  const key = login ? `login:${login}` : `name:${name}`;
  let contributor = contributors.get(key);
  if (!contributor) {
    contributor = {
      key,
      login: login || '',
      name: name || login || '未知贡献者',
      url: url || (login ? `https://github.com/${login}` : ''),
      commits: [],
      prs: new Map(),
    };
    contributors.set(key, contributor);
  }
  return contributor;
}

function contributorLabel(contributor) {
  if (contributor.login) return `[@${contributor.login}](${contributor.url})`;
  if (contributor.url) return `[${contributor.name}](${contributor.url})`;
  return contributor.name;
}

const escapeCell = (text) =>
  String(text || '')
    .replace(/\|/g, '\\|')
    .trim();

function renderNotes(model) {
  const {
    baseRef,
    compareUrl,
    headLabel,
    totalCommits,
    countedCommits,
    mergeCount,
    botCount,
    countedPrs,
    truncatedCommits,
    prLookupSkipped,
    contributors,
  } = model;

  const lines = [];
  lines.push('### 🤝 本次发布的贡献者');
  lines.push('');

  const summary = [
    `**${countedPrs.length}** 个 PR`,
    `**${countedCommits}** 个提交`,
  ];
  summary.push(`**${contributors.length}** 位贡献者`);
  lines.push(`> 本次发布共包含 ${summary.join('、')}。`);

  const scope = baseRef
    ? `\`${baseRef}...${headLabel}\``
    : `最近 ${totalCommits} 个提交`;
  lines.push(
    compareUrl
      ? `> 对比范围：[${scope}](${compareUrl})`
      : `> 对比范围：${scope}`,
  );

  const skipped = [];
  if (mergeCount) skipped.push(`${mergeCount} 条合并提交`);
  if (botCount) skipped.push(`${botCount} 条自动构建提交`);
  if (skipped.length) lines.push(`> 已忽略 ${skipped.join('、')}。`);
  if (truncatedCommits)
    lines.push(
      `> 提交数量超过上限，仅统计最近 ${MAX_RENDERED_COMMITS} 个提交。`,
    );
  if (prLookupSkipped)
    lines.push(`> 超出 PR 反查上限的 ${prLookupSkipped} 个提交未关联 PR。`);
  lines.push('');

  if (!countedCommits) {
    lines.push('> 本次发布没有检测到新的代码提交。');
    lines.push('');
    return lines.join('\n');
  }

  if (countedPrs.length) {
    lines.push('#### 📌 PR 一览');
    lines.push('');
    lines.push('| PR | 标题 | 贡献者 |');
    lines.push('| --- | --- | --- |');
    for (const pr of countedPrs) {
      const number = `[#${pr.number}](${pr.url})`;
      const author = pr.author
        ? `[@${pr.author}](${pr.authorUrl || `https://github.com/${pr.author}`})`
        : '未知';
      lines.push(`| ${number} | ${escapeCell(pr.title)} | ${author} |`);
    }
    lines.push('');
  } else {
    lines.push('> 本次发布没有关联到已合并的 PR。');
    lines.push('');
  }

  lines.push('#### 👤 按贡献者查看');
  lines.push('');

  for (const contributor of contributors) {
    const stats = [];
    if (contributor.commits.length)
      stats.push(`${contributor.commits.length} 个提交`);
    if (contributor.prs.size) stats.push(`${contributor.prs.size} 个 PR`);
    lines.push(
      `##### ${contributorLabel(contributor)} — ${stats.join(' · ') || '无提交记录'}`,
    );
    lines.push('');

    if (contributor.prs.size) {
      lines.push('**PR**');
      lines.push('');
      for (const pr of [...contributor.prs.values()].sort(
        (a, b) => a.number - b.number,
      )) {
        lines.push(`- [#${pr.number}](${pr.url}) ${pr.title}`);
      }
      lines.push('');
    }

    if (contributor.commits.length) {
      lines.push('**提交记录**');
      lines.push('');
      for (const commit of contributor.commits) {
        const suffix = commit.coAuthor ? '（协作）' : '';
        lines.push(
          `- [\`${commit.shortSha}\`](${commit.url}) ${commit.subject}${suffix}`,
        );
      }
      lines.push('');
    }
  }

  return lines.join('\n');
}

function renderFailure(reason) {
  return [
    '### 🤝 本次发布的贡献者',
    '',
    `> 贡献者信息生成失败：${reason}`,
    '> 本次发布说明不含该章节，详见 Actions 运行日志。',
    '',
  ].join('\n');
}

async function main() {
  if (!options.repo)
    throw new Error('缺少仓库信息（GITHUB_REPOSITORY 或 --repo）');

  const baseTag = options.base || (await pickBaseTag());
  // 用 collectCommits 回传的 baseRef：compare 失败回退到提交列表时会清空，避免给出不存在的对比区间
  const { baseRef, compareUrl, totalCommits, commits } =
    await collectCommits(baseTag);

  const normalized = (commits || []).map(toCommit);
  const mergeCount = normalized.filter((commit) => commit.isMerge).length;
  const nonMerge = normalized.filter((commit) => !commit.isMerge);
  const botCount = nonMerge.filter(isBotCommit).length;
  const humanAll = nonMerge.filter((commit) => !isBotCommit(commit));
  const human = humanAll.slice(-MAX_RENDERED_COMMITS);
  const truncatedCommits = humanAll.length > human.length;

  const prLimit = Number.isFinite(options.maxPrLookups)
    ? Math.max(0, options.maxPrLookups)
    : 250;
  const lookupTargets = human.slice(0, prLimit);
  const prLookupSkipped = human.length - lookupTargets.length;

  const prResults = await mapLimit(lookupTargets, 6, async (commit) => {
    try {
      const list = await request(
        `/repos/${options.repo}/commits/${commit.sha}/pulls`,
        { soft: true },
      );
      const pr = (list || []).find((item) => item.merged_at) || null;
      if (!pr) return null;
      return {
        number: pr.number,
        title: (pr.title || '').trim(),
        url: pr.html_url,
        author: pr.user?.login || '',
        authorUrl: pr.user?.html_url || '',
      };
    } catch (error) {
      console.warn(`反查 ${commit.shortSha} 的 PR 失败：${error.message}`);
      return null;
    }
  });

  const prBySha = new Map(
    lookupTargets.map((commit, index) => [
      commit.sha,
      prResults[index] || null,
    ]),
  );

  const prs = new Map();
  const contributors = new Map();

  for (const commit of human) {
    const pr = prBySha.get(commit.sha) || null;
    if (pr) prs.set(pr.number, pr);

    const author = ensureContributor(contributors, {
      login: commit.authorLogin,
      name: commit.authorName,
      url: commit.authorLogin ? `https://github.com/${commit.authorLogin}` : '',
    });
    author.commits.push(commit);

    if (pr) {
      // PR 记在 PR 作者名下；作者信息缺失时回退到提交作者。
      const prAuthor = ensureContributor(contributors, {
        login: pr.author || commit.authorLogin,
        name: pr.author || commit.authorName,
        url:
          pr.authorUrl || (pr.author ? `https://github.com/${pr.author}` : ''),
      });
      prAuthor.prs.set(pr.number, pr);
    }

    for (const coAuthor of parseCoAuthors(commit.message, commit.authorLogin)) {
      const owner = ensureContributor(contributors, coAuthor);
      owner.commits.push({ ...commit, coAuthor: true });
    }
  }

  const sortedContributors = [...contributors.values()].sort((a, b) => {
    const byCommits = b.commits.length - a.commits.length;
    if (byCommits) return byCommits;
    const byPrs = b.prs.size - a.prs.size;
    if (byPrs) return byPrs;
    return contributorLabel(a).localeCompare(contributorLabel(b));
  });

  const headLabel = /^[0-9a-f]{40}$/i.test(options.head)
    ? options.head.slice(0, 7)
    : options.head;

  return renderNotes({
    baseRef,
    compareUrl,
    headLabel,
    totalCommits,
    countedCommits: human.length,
    mergeCount,
    botCount,
    countedPrs: [...prs.values()].sort((a, b) => a.number - b.number),
    truncatedCommits,
    prLookupSkipped,
    contributors: sortedContributors,
  });
}

let output;
try {
  output = await main();
} catch (error) {
  console.error(`生成贡献者章节失败：${error.stack || error.message}`);
  output = renderFailure(error.message);
}

writeFileSync(options.out, `${output.trimEnd()}\n`, 'utf8');
process.stdout.write(`${output.trimEnd()}\n`);
