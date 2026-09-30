// @vitest-environment node
// Architecture boundaries (ADR 0006, ADR 0010, .kiro/steering/frontend.md "Domain-first layout"):
// app -> routes -> pages -> features -> common. Under features/<domain>/, every folder is a slice: a concept slice
// (listed in CONCEPT_SLICES), the optional `core`, or a use case. A slice is reached from outside only via its own
// index.ts barrel. Within a domain, a use case imports only its own files, the domain's concept slices and `core`;
// a concept slice imports only other concept slices and `core`; `core` imports no other slice of its domain; there
// are no import cycles between slices.
import ts from 'typescript';
import { describe, expect, it } from 'vitest';

/** Import graph keyed by src-relative path (forward slashes) -> src-relative resolved targets. */
type Graph = Map<string, string[]>;

const LAYERS: readonly string[] = ['app', 'routes', 'pages', 'features', 'common'];

/**
 * Slices named after a domain concept that several use cases of the domain share. Every other slice except `core` is
 * a use case. Add a slice here when it becomes shared; a use case never imports another use case.
 */
const CONCEPT_SLICES: ReadonlySet<string> = new Set(['auth/session', 'account/profile', 'account/password']);

/**
 * Layer of a file and, under features/, its slice `<domain>/<slice>` (`core` or a use case). A file directly in a
 * domain folder belongs to no slice and has `feature` `<domain>`.
 */
function locate(path: string): FileLocation | undefined {
  if (path === 'main.tsx') return { layer: 0 };
  if (path === 'routeTree.gen.ts') return { layer: 1 }; // generated route tree is routing code
  const [top, domain, child, ...rest] = path.split('/');
  const layer = LAYERS.indexOf(top);
  if (layer < 0) return undefined;
  if (top !== 'features') return { layer };
  return { layer, domain, feature: rest.length > 0 ? `${domain}/${child}` : domain };
}

type FileLocation = { layer: number; domain?: string; feature?: string };
type SliceKind = 'core' | 'concept' | 'use case';

// Which slice kinds each kind may import within its own domain.
const ALLOWED: Record<SliceKind, readonly SliceKind[]> = {
  core: [],
  concept: ['core', 'concept'],
  'use case': ['core', 'concept'],
};

function sliceKind(feature: string, domain: string, concepts: ReadonlySet<string>): SliceKind {
  if (feature === `${domain}/core`) return 'core';
  return concepts.has(feature) ? 'concept' : 'use case';
}

/** Feature-level import cycles, each as the example edges that close it. */
function findCycles(featureEdges: Map<string, Map<string, string>>): string[] {
  const cycles: string[] = [];
  const state = new Map<string, 'visiting' | 'done'>();
  const stack: string[] = [];
  const visit = (feature: string) => {
    state.set(feature, 'visiting');
    stack.push(feature);
    for (const next of featureEdges.get(feature)?.keys() ?? []) {
      if (state.get(next) === 'visiting') {
        const loop = [...stack.slice(stack.indexOf(next)), next];
        cycles.push(
          loop
            .slice(1)
            .map((to, i) => featureEdges.get(loop[i])?.get(to))
            .join(' | '),
        );
      } else if (!state.has(next)) visit(next);
    }
    stack.pop();
    state.set(feature, 'done');
  };
  for (const feature of featureEdges.keys()) if (!state.has(feature)) visit(feature);
  return cycles;
}

function checkBoundaries(graph: Graph, concepts: ReadonlySet<string> = CONCEPT_SLICES) {
  const layer: string[] = [];
  const crossFeature: string[] = [];
  const outsideFeature: string[] = [];
  const unsliced: string[] = [];
  const withinDomain: string[] = [];
  const featureEdges = new Map<string, Map<string, string>>(); // from feature -> to feature -> example edge

  const checkEdge = (file: string, from: FileLocation, target: string, to: FileLocation) => {
    const edge = `${file} -> ${target}`;
    if (to.layer < from.layer) layer.push(`${edge} (${LAYERS[from.layer]} must not import ${LAYERS[to.layer]})`);
    if (to.feature === undefined || to.feature === from.feature) return;
    if (target !== `features/${to.feature}/index.ts`) (from.feature ? crossFeature : outsideFeature).push(edge);
    if (!from.feature) return;
    if (
      from.domain === to.domain &&
      to.domain !== undefined &&
      !ALLOWED[sliceKind(from.feature, to.domain, concepts)].includes(sliceKind(to.feature, to.domain, concepts))
    )
      withinDomain.push(edge);
    const out = featureEdges.get(from.feature) ?? new Map<string, string>();
    if (!out.has(to.feature)) out.set(to.feature, edge);
    featureEdges.set(from.feature, out);
  };

  for (const [file, targets] of graph) {
    const from = locate(file);
    if (!from) continue;
    if (from.feature === from.domain && from.domain !== undefined) unsliced.push(file);
    for (const target of targets) {
      const to = locate(target);
      if (to) checkEdge(file, from, target, to);
    }
  }

  return { layer, crossFeature, outsideFeature, unsliced, withinDomain, cycles: findCycles(featureEdges) };
}

function moduleSpecifiers(fileName: string, text: string): string[] {
  const source = ts.createSourceFile(
    fileName,
    text,
    ts.ScriptTarget.Latest,
    false,
    fileName.endsWith('.tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.TS,
  );
  const specifiers: string[] = [];
  const visit = (node: ts.Node) => {
    if (
      (ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) &&
      node.moduleSpecifier &&
      ts.isStringLiteral(node.moduleSpecifier)
    ) {
      specifiers.push(node.moduleSpecifier.text);
    } else if (
      ts.isCallExpression(node) &&
      node.expression.kind === ts.SyntaxKind.ImportKeyword &&
      node.arguments[0] &&
      ts.isStringLiteralLike(node.arguments[0])
    ) {
      specifiers.push(node.arguments[0].text);
    } else if (
      ts.isImportTypeNode(node) &&
      ts.isLiteralTypeNode(node.argument) &&
      ts.isStringLiteral(node.argument.literal)
    ) {
      specifiers.push(node.argument.literal.text);
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return specifiers;
}

function buildSourceGraph(): Graph {
  const configPath = ts.findConfigFile(ts.sys.getCurrentDirectory(), ts.sys.fileExists, 'tsconfig.app.json');
  if (!configPath) throw new Error('tsconfig.app.json not found; run vitest from frontend/');
  const config = ts.getParsedCommandLineOfConfigFile(
    configPath,
    {},
    {
      ...ts.sys,
      onUnRecoverableConfigFileDiagnostic: (d) => {
        throw new Error(ts.flattenDiagnosticMessageText(d.messageText, '\n'));
      },
    },
  );
  if (!config) throw new Error(`Could not parse ${configPath}`);
  const srcDir = `${configPath.slice(0, configPath.lastIndexOf('/'))}/src/`;
  const relative = (path: string) => (path.startsWith(srcDir) ? path.slice(srcDir.length) : undefined);

  const graph: Graph = new Map();
  for (const fileName of config.fileNames) {
    const file = relative(fileName);
    if (
      !file ||
      !/\.tsx?$/.test(file) ||
      file.endsWith('.d.ts') ||
      /\.test\.tsx?$/.test(file) ||
      file === 'test-setup.ts' ||
      file === 'routeTree.gen.ts'
    )
      continue;
    const targets: string[] = [];
    for (const specifier of moduleSpecifiers(fileName, ts.sys.readFile(fileName) ?? '')) {
      const { resolvedModule } = ts.resolveModuleName(specifier, fileName, config.options, ts.sys);
      if (!resolvedModule || resolvedModule.isExternalLibraryImport) continue; // bare package imports
      const target = relative(resolvedModule.resolvedFileName);
      if (target) targets.push(target);
    }
    graph.set(file, targets);
  }
  return graph;
}

describe('architecture boundaries', () => {
  const graph = buildSourceGraph();
  const result = checkBoundaries(graph);

  it('sees the source tree', () => {
    expect(graph.has('main.tsx')).toBe(true);
  });

  it('no layer imports a layer to its left (app -> routes -> pages -> features -> common)', () => {
    expect(result.layer).toEqual([]);
  });

  it('features import other features only through their index.ts barrel', () => {
    expect(result.crossFeature).toEqual([]);
  });

  it('code outside a feature imports it only through its index.ts barrel', () => {
    expect(result.outsideFeature).toEqual([]);
  });

  it('has no import cycles between features', () => {
    expect(result.cycles).toEqual([]);
  });

  it('puts every domain file in its core or a use case, never directly in the domain folder', () => {
    expect(result.unsliced).toEqual([]);
  });

  it('within a domain, use cases import only concept slices and the core, and the core imports no other slice', () => {
    expect(result.withinDomain).toEqual([]);
  });

  it('self-check: detects each kind of violation across domains', () => {
    const synthetic = checkBoundaries(
      new Map([
        ['common/a.ts', ['features/x/core/model/m.ts']],
        ['features/x/core/model/m.ts', ['features/y/core/index.ts', 'features/x/core/index.ts']],
        ['features/y/core/api/b.ts', ['features/x/core/model/m.ts']],
        ['pages/p.tsx', ['features/x/core/model/m.ts', 'features/y/core/index.ts']],
      ]),
    );
    expect(synthetic).toEqual({
      layer: ['common/a.ts -> features/x/core/model/m.ts (common must not import features)'],
      crossFeature: ['features/y/core/api/b.ts -> features/x/core/model/m.ts'],
      outsideFeature: ['common/a.ts -> features/x/core/model/m.ts', 'pages/p.tsx -> features/x/core/model/m.ts'],
      unsliced: [],
      withinDomain: [],
      cycles: [
        'features/x/core/model/m.ts -> features/y/core/index.ts | features/y/core/api/b.ts -> features/x/core/model/m.ts',
      ],
    });
  });

  it('self-check: use cases and concept slices may import concept slices; the core may not', () => {
    const synthetic = checkBoundaries(
      new Map([
        ['features/d/u/hooks/h.ts', ['features/d/c/index.ts']],
        ['features/d/c/model/m.ts', ['features/d/k/index.ts', 'features/d/core/index.ts']],
        ['features/d/core/model/r.ts', ['features/d/c/index.ts']],
        ['features/d/k/model/n.ts', ['features/d/u/index.ts']],
      ]),
      new Set(['d/c', 'd/k']),
    );
    expect(synthetic.withinDomain).toEqual([
      'features/d/core/model/r.ts -> features/d/c/index.ts',
      'features/d/k/model/n.ts -> features/d/u/index.ts',
    ]);
  });

  it('self-check: slices a domain into its core and use cases', () => {
    const synthetic = checkBoundaries(
      new Map([
        [
          'features/d/u/hooks/h.ts',
          ['features/d/core/index.ts', 'features/d/core/model/m.ts', 'features/d/u/model/n.ts'],
        ],
        ['features/d/core/model/m.ts', ['features/d/u/index.ts']],
        ['features/d/v/api/a.ts', ['features/d/u/index.ts']],
        ['features/d/index.ts', []],
      ]),
    );
    expect(synthetic).toEqual({
      layer: [],
      crossFeature: ['features/d/u/hooks/h.ts -> features/d/core/model/m.ts'],
      outsideFeature: [],
      unsliced: ['features/d/index.ts'],
      withinDomain: [
        'features/d/core/model/m.ts -> features/d/u/index.ts',
        'features/d/v/api/a.ts -> features/d/u/index.ts',
      ],
      cycles: [
        'features/d/u/hooks/h.ts -> features/d/core/index.ts | features/d/core/model/m.ts -> features/d/u/index.ts',
      ],
    });
  });
});
