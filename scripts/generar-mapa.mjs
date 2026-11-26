// Genera mapas compactos de PRISALY-Front y PRISALY-Backen para que los agentes sepan dónde buscar.
// Uso (desde la raíz Prisaly):  node .claude/mapa/generar-mapa.mjs [front|backend|datos|docs] [--front <ruta>] [--backend <ruta>]
//   Sin argumento genera los tres. Mapea la rama que esté activa en cada carpeta.
// Solo lee archivos y ejecuta `git rev-parse` / `git branch`. No modifica los repos.
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const OUT = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(OUT, "..", "..");
// --front <ruta> / --backend <ruta> permiten mapear un worktree (p. ej. el de Codex) en lugar de la copia de la raíz.
const opt = (n) => { const i = process.argv.indexOf(n); return i > 0 ? path.resolve(process.argv[i + 1]) : null; };
const FRONT = opt("--front") ?? path.join(ROOT, "PRISALY-Front");
const BACK = opt("--backend") ?? path.join(ROOT, "PRISALY-Backen");
const SKIP = new Set(["node_modules", "dist", ".angular", "target", ".git", ".mvn", "build"]);

const rel = (base, p) => path.relative(base, p).split(path.sep).join("/");
const read = (p) => fs.readFileSync(p, "utf8");
const lineOf = (txt, idx) => txt.slice(0, idx).split("\n").length;

function walk(dir, filter, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (SKIP.has(e.name)) continue;
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, filter, out);
    else if (filter(p)) out.push(p);
  }
  return out.sort();
}

function git(repo, ...args) {
  try { return execFileSync("git", ["-C", repo, ...args], { encoding: "utf8" }).trim(); } catch { return "?"; }
}
function cabecera(titulo, repo) {
  const sha = git(repo, "rev-parse", "--short", "HEAD");
  const rama = git(repo, "rev-parse", "--abbrev-ref", "HEAD");
  const sucio = git(repo, "status", "--porcelain").length ? " (con cambios sin commit)" : "";
  return [
    `# ${titulo}`,
    "",
    `Generado: ${new Date().toISOString().slice(0, 16).replace("T", " ")} · rama \`${rama === "HEAD" ? "HEAD separado" : rama}\` · SHA \`${sha}\`${sucio}`,
    "",
    `> Si \`git -C ${path.basename(repo)} rev-parse --short HEAD\` no da \`${sha}\`, este mapa está desactualizado: regenéralo con \`node .claude/mapa/generar-mapa.mjs\` antes de usarlo.`,
    "> El mapa dice **dónde** está cada cosa. Para saber **cómo** funciona, lee solo los archivos que señala.",
    "",
  ];
}

// ---------------------------------------------------------------- Front
function mapaFront() {
  const app = path.join(FRONT, "src", "app");
  const L = cabecera("Mapa de PRISALY-Front", FRONT);

  // Rutas
  L.push("## Rutas", "", "| Ruta | Guard | Destino | Archivo:línea |", "|---|---|---|---|");
  for (const f of walk(app, (p) => p.endsWith(".routes.ts"))) {
    const t = read(f);
    const re = /path:\s*'([^']*)'/g;
    const hits = [...t.matchAll(re)];
    hits.forEach((m, i) => {
      const trozo = t.slice(m.index, hits[i + 1]?.index ?? t.length);
      const guard = (trozo.match(/canActivate:\s*\[([^\]]*)\]/)?.[1] ?? "").replace(/\s+/g, " ").trim();
      const destino =
        trozo.match(/redirectTo:\s*'([^']*)'/)?.[1]?.replace(/^/, "→ /") ??
        trozo.match(/import\('([^']+)'\)(?:\.then\(\(m\)\s*=>\s*m\.(\w+)\))?/)?.slice(1).filter(Boolean).join(" · ") ??
        (trozo.match(/children:/) ? "(hijas)" : "");
      L.push(`| \`/${m[1]}\` | ${guard ? "`" + guard.slice(0, 70) + "`" : "—"} | ${destino} | ${rel(FRONT, f)}:${lineOf(t, m.index)} |`);
    });
  }

  // Entornos: claves booleanas por entorno
  const envDir = path.join(FRONT, "src", "environments");
  const envs = walk(envDir, (p) => /^environment.*\.ts$/.test(path.basename(p)));
  const tabla = new Map();
  for (const f of envs) {
    const n = path.basename(f, ".ts").replace("environment.", "").replace("environment", "base");
    for (const m of read(f).matchAll(/^\s*(\w+):\s*(true|false)\b/gm)) {
      if (!tabla.has(m[1])) tabla.set(m[1], {});
      tabla.get(m[1])[n] = m[2] === "true" ? "sí" : "no";
    }
  }
  const nombres = envs.map((f) => path.basename(f, ".ts").replace("environment.", "").replace("environment", "base"));
  L.push("", "## Opciones booleanas por entorno (`src/environments`)", "", `| Opción | ${nombres.join(" | ")} |`, `|---|${nombres.map(() => "---").join("|")}|`);
  for (const [k, v] of [...tabla].sort()) L.push(`| \`${k}\` | ${nombres.map((n) => v[n] ?? "·").join(" | ")} |`);
  const otros = walk(envDir, (p) => p.endsWith(".ts") && !/environment/.test(path.basename(p)));
  if (otros.length) L.push("", `Otros archivos de entorno: ${otros.map((f) => "`" + rel(FRONT, f) + "`").join(", ")}`);

  // Llamadas HTTP
  L.push("", "## Rutas de API usadas por el Front", "", "| Ruta API | Archivo:línea |", "|---|---|");
  for (const f of walk(app, (p) => p.endsWith(".ts") && !p.endsWith(".spec.ts"))) {
    const t = read(f);
    for (const m of t.matchAll(/['`]\/?(v1\/[^'`\s]*)['`]/g)) L.push(`| \`${m[1]}\` | ${rel(FRONT, f)}:${lineOf(t, m.index)} |`);
  }

  // Archivos por carpeta con sus exportaciones
  L.push("", "## Archivos de `src/app` (sin specs) y lo que exportan", "");
  const porDir = new Map();
  for (const f of walk(app, (p) => p.endsWith(".ts") && !p.endsWith(".spec.ts"))) {
    const d = rel(app, path.dirname(f)) || ".";
    if (!porDir.has(d)) porDir.set(d, []);
    const ex = [...read(f).matchAll(/export\s+(?:default\s+)?(?:abstract\s+)?(?:class|const|function|interface|type|enum)\s+(\w+)/g)].map((m) => m[1]);
    porDir.get(d).push(`${path.basename(f)}${ex.length ? " — " + ex.slice(0, 5).join(", ") + (ex.length > 5 ? ` (+${ex.length - 5})` : "") : ""}`);
  }
  const specs = walk(app, (p) => p.endsWith(".spec.ts"));
  for (const [d, fs_] of porDir) {
    const nSpec = specs.filter((s) => rel(app, path.dirname(s)) === d).length;
    L.push(`**${d}/** (${fs_.length} archivos${nSpec ? `, ${nSpec} specs` : ""})`);
    for (const x of fs_) L.push(`- ${x}`);
    L.push("");
  }
  return L;
}

// ---------------------------------------------------------------- Backend
function aplanarYaml(txt) {
  const out = [];
  const pila = [];
  txt.split(/\r?\n/).forEach((linea, i) => {
    if (!linea.trim() || linea.trim().startsWith("#") || linea.trim().startsWith("-")) return;
    const m = linea.match(/^(\s*)([\w.-]+):\s*(.*)$/);
    if (!m) return;
    const nivel = m[1].length;
    while (pila.length && pila[pila.length - 1].nivel >= nivel) pila.pop();
    pila.push({ nivel, k: m[2] });
    const valor = m[3].replace(/\s+#.*$/, "").trim();
    if (valor) out.push({ clave: pila.map((p) => p.k).join("."), valor, linea: i + 1 });
  });
  return out;
}

function mapaBackend() {
  const L = cabecera("Mapa de PRISALY-Backen", BACK);
  const java = walk(BACK, (p) => p.endsWith(".java") && p.includes(`${path.sep}main${path.sep}`));

  // Módulos
  L.push("## Módulos Maven", "", "| Módulo | Clases (main) | Paquetes principales |", "|---|---|---|");
  for (const mod of fs.readdirSync(BACK).filter((d) => fs.existsSync(path.join(BACK, d, "pom.xml"))).sort()) {
    const base = path.join(BACK, mod, "src", "main", "java");
    const fsMod = java.filter((f) => f.startsWith(base + path.sep));
    const paquetes = new Map();
    for (const f of fsMod) {
      const pk = rel(base, path.dirname(f)).split("/").slice(0, 4).join(".");
      paquetes.set(pk, (paquetes.get(pk) ?? 0) + 1);
    }
    const top = [...paquetes].sort((a, b) => b[1] - a[1]).slice(0, 6).map(([k, n]) => `\`${k}\` (${n})`).join(", ");
    L.push(`| ${mod} | ${fsMod.length} | ${top} |`);
  }

  // Endpoints
  const yml = path.join(BACK, "app", "src", "main", "resources", "application.yml");
  const ctx = fs.existsSync(yml) ? (read(yml).match(/context-path:\s*(\S+)/)?.[1] ?? "") : "";
  L.push("", `## Endpoints (prefijo de contexto \`${ctx || "ninguno"}\`)`, "", "| Método | Ruta | Entrada (cuerpo) | Salida | Controlador:línea | Condición (flag) |", "|---|---|---|---|---|---|");
  for (const f of java) {
    const t = read(f);
    if (!/@(Rest)?Controller\b/.test(t)) continue;
    const posClase = t.search(/\bclass\s+\w+/);
    const cond = [...t.slice(0, posClase).matchAll(/@ConditionalOnProperty\(([^)]*)\)/g)].map((m) => {
      const a = m[1].replace(/"/g, "");
      const pre = a.match(/prefix\s*=\s*([^,]+)/)?.[1]?.trim();
      const nom = (a.match(/(?:name|value)\s*=\s*([^,]+)/)?.[1] ?? a).trim();
      return pre ? `${pre}.${nom}` : nom;
    }).join(", ");
    let base = "";
    const re = /@(Get|Post|Put|Patch|Delete|Request)Mapping\b(\s*\(([^)]*)\))?/g;
    for (const m of t.matchAll(re)) {
      const args = m[3] ?? "";
      const ruta = args.match(/"([^"]*)"/)?.[1] ?? (args.match(/(?:value|path)\s*=\s*([\w.]+)/)?.[1] ?? "");
      if (m.index < posClase && m[1] === "Request") { base = ruta; continue; }
      const metodo = m[1] === "Request" ? (args.match(/RequestMethod\.(\w+)/)?.[1] ?? "ANY") : m[1].toUpperCase();
      const { entrada, salida } = firmaMetodo(t, m.index + m[0].length);
      L.push(`| ${metodo} | \`${(base + ruta) || "/"}\` | ${entrada ? "`" + entrada + "`" : "—"} | ${salida ? "`" + salida + "`" : "—"} | ${rel(BACK, f).replace(/.*src\/main\/java\//, "")}:${lineOf(t, m.index)} | ${cond ? "`" + cond + "`" : "—"} |`);
    }
  }

  // Flags
  if (fs.existsSync(yml)) {
    L.push("", "## Flags `enabled` de `app/src/main/resources/application.yml`", "", "| Clave | Valor por defecto | Variable de entorno | Línea |", "|---|---|---|---|");
    for (const { clave, valor, linea } of aplanarYaml(read(yml)).filter((x) => x.clave.endsWith(".enabled"))) {
      const env = valor.match(/^\$\{(\w+):([^}]*)\}$/);
      L.push(`| \`${clave}\` | ${env ? env[2] : valor} | ${env ? "`" + env[1] + "`" : "—"} | ${linea} |`);
    }
  }

  // Migraciones
  const migs = walk(BACK, (p) => p.endsWith(".sql") && /db[\\/]/.test(p));
  if (migs.length) {
    L.push("", "## Scripts SQL (`db/`)", "");
    const porDir = new Map();
    for (const f of migs) { const d = rel(BACK, path.dirname(f)); if (!porDir.has(d)) porDir.set(d, []); porDir.get(d).push(path.basename(f)); }
    for (const [d, n] of porDir) L.push(`- \`${d}/\`: ${n.join(", ")}`);
  }
  return L;
}

// ---------------------------------------------------------------- Modelo de datos
// Devuelve el contenido entre el delimitador de apertura en `i` y su cierre equilibrado.
function equilibrado(t, i, abre = "(", cierra = ")") {
  let n = 0;
  for (let j = i; j < t.length; j++) {
    if (t[j] === abre) n++;
    else if (t[j] === cierra && --n === 0) return t.slice(i + 1, j);
  }
  return "";
}
function partirComas(s) {
  const out = []; let n = 0, ini = 0;
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
    if ("(<[{".includes(c)) n++; else if (")>]}".includes(c)) n--;
    else if (c === "," && n === 0) { out.push(s.slice(ini, i)); ini = i + 1; }
  }
  out.push(s.slice(ini));
  return out.map((x) => x.trim()).filter(Boolean);
}
const sinComillas = (s) => s.replace(/"/g, "");
const corto = (s, n) => (s.length > n ? s.slice(0, n - 1) + "…" : s);

function firmaMetodo(t, desde) {
  const sig = t.slice(desde, t.indexOf("{", desde)).replace(/\s+/g, " ");
  const entrada = sig.match(/@RequestBody(?:\s*\([^)]*\))?\s+(?:@\w+(?:\([^)]*\))?\s+)*([\w.]+(?:<[\w.<>, ?]+>)?)/)?.[1] ?? "";
  let salida = sig.match(/^\s*((?:(?:public|protected|private|static|final)\s+)*)([\w.]+(?:<[\w.<>, ?]+>)?)\s+\w+\s*\(/)?.[2] ?? "";
  salida = salida.replace(/^ResponseEntity<(.*)>$/, "$1");
  return { entrada: entrada.trim(), salida: salida === "void" ? "" : salida };
}

function tablasSql() {
  const sqls = walk(BACK, (p) => p.endsWith(".sql") && /[\\/]main[\\/]resources[\\/]db[\\/]/.test(p));
  const tablas = new Map();
  const tabla = (n) => {
    const k = n.toLowerCase();
    if (!tablas.has(k)) tablas.set(k, { nombre: n, columnas: [], pk: [], fk: [], origen: [] });
    return tablas.get(k);
  };
  for (const f of sqls) {
    const t = read(f).replace(/--.*$/gm, "");
    const carpeta = path.basename(path.dirname(f));
    const etiqueta = `${path.basename(f).split("__")[0]}${carpeta === "migration" ? "" : " (" + carpeta + ")"}`;
    for (const m of t.matchAll(/CREATE TABLE\s+(?:IF NOT EXISTS\s+)?("?[\w.]+"?)\s*\(/gi)) {
      const tb = tabla(sinComillas(m[1]).split(".").pop());
      tb.origen.push(etiqueta);
      for (const parte of partirComas(equilibrado(t, m.index + m[0].length - 1))) {
        const p = parte.replace(/\s+/g, " ");
        if (/^(CONSTRAINT|PRIMARY|UNIQUE|FOREIGN|CHECK|EXCLUDE)\b/i.test(p)) {
          const pk = p.match(/PRIMARY KEY\s*\(([^)]*)\)/i); if (pk) tb.pk.push(...sinComillas(pk[1]).split(/\s*,\s*/));
          const fk = p.match(/FOREIGN KEY\s*\(([^)]*)\)\s*REFERENCES\s+("?[\w.]+"?)/i);
          if (fk) tb.fk.push(`${sinComillas(fk[1])} → ${sinComillas(fk[2])}`);
          continue;
        }
        const c = p.match(/^("?[\w]+"?)\s+(.+?)(?=\s+(?:NOT|NULL|DEFAULT|GENERATED|REFERENCES|PRIMARY|UNIQUE|CHECK|CONSTRAINT)\b|$)/i);
        if (!c) continue;
        const nombre = sinComillas(c[1]);
        const nn = /NOT NULL|PRIMARY KEY/i.test(p);
        tb.columnas.push(`${nombre} ${c[2].toLowerCase()}${nn ? "" : "?"}`);
        if (/PRIMARY KEY/i.test(p)) tb.pk.push(nombre);
        const ref = p.match(/REFERENCES\s+("?[\w.]+"?)/i); if (ref) tb.fk.push(`${nombre} → ${sinComillas(ref[1])}`);
      }
    }
    for (const m of t.matchAll(/ALTER TABLE\s+(?:ONLY\s+)?(?:IF EXISTS\s+)?("?[\w.]+"?)\s+([\s\S]*?);/gi)) {
      const tb = tabla(sinComillas(m[1]).split(".").pop());
      const acc = m[2].replace(/\s+/g, " ");
      for (const c of acc.matchAll(/ADD COLUMN\s+(?:IF NOT EXISTS\s+)?("?\w+"?)\s+([\w() ,]+?)(?=\s+(?:NOT|NULL|DEFAULT|REFERENCES|CHECK)\b|,|$)/gi))
        tb.columnas.push(`${sinComillas(c[1])} ${c[2].toLowerCase().trim()} (+${etiqueta})`);
      for (const fk of acc.matchAll(/FOREIGN KEY\s*\(([^)]*)\)\s*REFERENCES\s+("?[\w.]+"?)/gi))
        tb.fk.push(`${sinComillas(fk[1])} → ${sinComillas(fk[2])}`);
    }
  }
  return [...tablas.values()].filter((x) => x.origen.length || x.columnas.length);
}

function modeloDatosBackend(java) {
  const L = [];
  // Entidades JPA → tabla
  const entidades = new Map();
  for (const f of java) {
    const t = read(f);
    if (!/@Entity\b/.test(t)) continue;
    const clase = t.match(/class\s+(\w+)/)?.[1];
    const crudo = t.match(/@Table\s*\(\s*(?:name\s*=\s*)?"((?:[^"\\]|\\.)*)"/)?.[1] ?? clase ?? "";
    const tb = crudo.replace(/\\"/g, "").replace(/"/g, "");
    entidades.set(tb.toLowerCase(), `${clase} (${rel(BACK, f).replace(/.*src\/main\/java\//, "")})`);
  }
  const tablas = tablasSql();
  L.push("", "## Modelo de datos: tablas", "",
    "Columnas como `nombre tipo`; `?` = admite NULL; `(+Vxxx)` = columna añadida después. Las tablas de carpetas `*-propuesta` **no** están en Flyway productivo.", "");
  for (const tb of tablas.sort((a, b) => a.nombre.localeCompare(b.nombre))) {
    const ent = entidades.get(tb.nombre.toLowerCase());
    L.push(`### ${tb.nombre}  ·  ${tb.origen.join(", ") || "solo ALTER"}${ent ? "  ·  entidad " + ent : ""}`);
    if (tb.columnas.length) L.push(`- Columnas: ${tb.columnas.map((c) => "`" + c + "`").join(", ")}`);
    if (tb.pk.length) L.push(`- PK: ${[...new Set(tb.pk)].join(", ")}`);
    if (tb.fk.length) L.push(`- FK: ${[...new Set(tb.fk)].join("; ")}`);
  }
  const sinTabla = [...entidades].filter(([k]) => !tablas.some((t) => t.nombre.toLowerCase() === k));
  if (sinTabla.length) L.push("", `Entidades JPA cuya tabla no aparece en los SQL: ${sinTabla.map(([k, v]) => `\`${k}\` → ${v}`).join("; ")}`);

  // DTO (records)
  L.push("", "## Modelo de datos: DTO y records (por módulo)", "");
  const porMod = new Map();
  for (const f of java) {
    const t = read(f);
    for (const m of t.matchAll(/public\s+record\s+(\w+)\s*(?:<[^>]*>)?\s*\(/g)) {
      const campos = partirComas(equilibrado(t, m.index + m[0].length - 1))
        .map((c) => c.replace(/@\w+(\([^)]*\))?\s*/g, "").trim().replace(/\s+/g, " "))
        .map((c) => { const i = c.lastIndexOf(" "); return i > 0 ? `${c.slice(i + 1)}: ${c.slice(0, i)}` : c; });
      const mod = rel(BACK, f).split("/")[0];
      if (!porMod.has(mod)) porMod.set(mod, []);
      porMod.get(mod).push(`\`${m[1]}\`(${corto(campos.join(", "), 180)}) — ${path.basename(f)}:${lineOf(t, m.index)}`);
    }
  }
  for (const [mod, regs] of [...porMod].sort()) { L.push(`**${mod}** (${regs.length})`); for (const r of regs) L.push(`- ${r}`); L.push(""); }
  return L;
}

function modelosFront(app) {
  const L = ["", "## Modelo de datos del Front (interfaces y tipos)", "",
    "De `*.models.ts`, `*.api.ts` y `*http*.ts`. Campos `?` = opcionales.", ""];
  const archivos = walk(app, (p) => /(\.models?\.ts|\.api\.ts|http[\w.-]*\.ts)$/.test(path.basename(p)) && !p.endsWith(".spec.ts"));
  for (const f of archivos) {
    const t = read(f);
    const filas = [];
    for (const m of t.matchAll(/export\s+interface\s+(\w+)(?:<[^>{]*>)?(?:\s+extends\s+([^{]+))?\s*\{/g)) {
      let cuerpo = equilibrado(t, m.index + m[0].length - 1, "{", "}");
      while (/\{[^{}]*\}/.test(cuerpo)) cuerpo = cuerpo.replace(/\{[^{}]*\}/g, "…");
      const campos = [...cuerpo.matchAll(/^\s*(?:readonly\s+)?(\w+)(\??):/gm)].map((c) => c[1] + c[2]);
      filas.push(`- \`${m[1]}\`${m[2] ? " extends " + m[2].trim() : ""}: ${corto(campos.join(", "), 200)}`);
    }
    for (const m of t.matchAll(/export\s+type\s+(\w+)(?:<[^>]*>)?\s*=\s*([^;]+);/g))
      filas.push(`- tipo \`${m[1]}\` = ${corto(m[2].replace(/\s+/g, " ").trim(), 140)}`);
    if (filas.length) { L.push(`**${rel(app, f)}**`, ...filas, ""); }
  }
  return L;
}

function mapaDatos() {
  const L = cabecera("Mapa de datos de PRISALY (Backend y Front)", BACK);
  const front = `rama \`${git(FRONT, "rev-parse", "--abbrev-ref", "HEAD")}\` · SHA \`${git(FRONT, "rev-parse", "--short", "HEAD")}\``;
  L[2] = L[2].replace("Generado:", "Generado (Backend):") + ` · Front: ${front}`;
  L.push("Qué datos existen, dónde se guardan y cómo viajan: tablas de PostgreSQL (Flyway), entidades JPA, DTO y records de Spring, e interfaces del Front. Qué endpoint usa cada DTO está en el mapa del Backend (columnas Entrada y Salida).", "");
  const java = walk(BACK, (p) => p.endsWith(".java") && p.includes(`${path.sep}main${path.sep}`));
  L.push(...modeloDatosBackend(java));
  L.push(...modelosFront(path.join(FRONT, "src", "app")));
  return L;
}

// ---------------------------------------------------------------- Documentos
function titulo(f) {
  const t = read(f).slice(0, 3000);
  return (t.match(/^#\s+(.+)$/m)?.[1] ?? "").replace(/\|/g, "/").slice(0, 110);
}
function mapaDocs() {
  const L = ["# Índice de documentos de PRISALY", "", `Generado: ${new Date().toISOString().slice(0, 16).replace("T", " ")}`, "",
    "> Lee solo el documento que necesites. Los documentos son contexto; el código del SHA actual manda.", ""];
  const zonas = [["PRISALY-Front/docs", FRONT, "docs"], ["PRISALY-Backen/docs", BACK, "docs"], ["Entregables", ROOT, "Entregables"]];
  for (const [nombre, base, sub] of zonas) {
    const dir = path.join(base, sub);
    const mds = walk(dir, (p) => p.endsWith(".md"));
    if (!mds.length) continue;
    L.push(`## ${nombre} (${mds.length})`, "");
    let actual = null;
    for (const f of mds) {
      const d = rel(dir, path.dirname(f)) || ".";
      if (d !== actual) { L.push(`**${d}/**`); actual = d; }
      const st = fs.statSync(f);
      L.push(`- ${path.basename(f)} · ${st.mtime.toISOString().slice(0, 10)}${titulo(f) ? " — " + titulo(f) : ""}`);
    }
    L.push("");
  }
  return L;
}

const pedir = ["front", "backend", "datos", "docs"].includes(process.argv[2]) ? process.argv[2] : null;
const trabajos = { front: ["MAPA-FRONT.md", mapaFront], backend: ["MAPA-BACKEN.md", mapaBackend], datos: ["MAPA-DATOS.md", mapaDatos], docs: ["MAPA-DOCS.md", mapaDocs] };
for (const [k, [archivo, fn]] of Object.entries(trabajos)) {
  if (pedir && pedir !== k) continue;
  const texto = fn().join("\n") + "\n";
  fs.writeFileSync(path.join(OUT, archivo), texto);
  console.log(`${archivo}: ${texto.split("\n").length} líneas, ~${Math.round(texto.length / 4)} tokens`);
}
