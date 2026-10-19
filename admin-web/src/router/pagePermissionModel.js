// The server registry supplies every configurable page and button. This file renders its navigation view.
export const todoOverviewPermissions = [
  'BUTTON_APPROVALS_TODO_APPROVE', 'BUTTON_APPROVALS_TODO_RETURN', 'BUTTON_EXPENSE_UNASSIGNED_ATTRIBUTE'
];

export const routeAccessCodes = (route) => route?.permission
  ? (Array.isArray(route.permission) ? route.permission : [route.permission]) : [];

export function overviewCardAccess(codes) {
  const grants = new Set(codes);
  return {
    approvals: grants.has('PAGE_APPROVALS_TODO')
      && (grants.has('BUTTON_APPROVALS_TODO_APPROVE') || grants.has('BUTTON_APPROVALS_TODO_RETURN')),
    attribution: grants.has('PAGE_EXPENSE_UNASSIGNED') && grants.has('BUTTON_EXPENSE_UNASSIGNED_ATTRIBUTE')
  };
}

export function hasRouteAccess(route, selectedCodes) {
  if (route?.key === 'my-todos') return Object.values(overviewCardAccess(selectedCodes)).some(Boolean);
  return routeAccessCodes(route).some((code) => selectedCodes.includes(code));
}

export function buildPagePermissionTree(navigation, routes, catalog) {
  const permissions = new Map(catalog.filter((item) => item.status !== 0)
    .map((item) => [item.permissionCode, item]));
  const routeMap = new Map(routes.map((route) => [route.key, route]));
  const pageNode = (key, label) => {
    const route = routeMap.get(key);
    if (!route && !key.startsWith('mini-')) throw new Error(`Unknown navigation route: ${key}`);
    if (key === 'my-todos') return { type: 'derived', key, label: route.label, codes: [] };
    const code = `PAGE_${key.replaceAll('-', '_').toUpperCase()}`;
    if (!permissions.has(code)) throw new Error(`Unregistered page: ${code}`);
    const operations = [...permissions.values()].filter((item) => item.permissionType === 'BUTTON'
      && item.parentCode === code).map((item) => item.permissionCode);
    return { type: 'page', key, label: label || permissions.get(code).permissionName,
      access: [code], operations, codes: [code, ...operations], shared: [], accessMode: 'SINGLE' };
  };
  const child = (item) => {
    if (typeof item === 'string') return pageNode(item);
    const children = (item.children || []).map(child);
    return { type: 'group', key: item.key || item.label, label: item.label, children,
      codes: [...new Set(children.flatMap((entry) => entry.codes))] };
  };
  const domains = navigation.map(child);
  for (const domain of domains) {
    const miniPages = [...permissions.values()].filter((item) => item.permissionType === 'PAGE'
      && item.pageKey?.startsWith('mini-') && item.domain === domain.label).map((item) => pageNode(item.pageKey));
    if (miniPages.length) {
      const codes = miniPages.flatMap((item) => item.codes);
      domain.children.push({ type: 'group', key: `${domain.key}-mini`, label: '小程序', children: miniPages, codes });
      domain.codes = [...new Set([...domain.codes, ...codes])];
    }
  }
  const listed = new Set(domains.flatMap(function collect(node) {
    return node.type === 'page' ? node.codes : (node.children || []).flatMap(collect);
  }));
  const common = [...permissions.values()].filter((item) => !listed.has(item.permissionCode));
  return { domains, common, permissions };
}

export function selectPermissionCodes(current, changes, selected, routes, ownerKey = '', catalog = []) {
  const next = new Set(current);
  const metadata = new Map(catalog.map((item) => [item.permissionCode, item]));
  const all = [...metadata.values()];
  for (const code of changes) {
    if (selected) {
      next.add(code);
      const parent = metadata.get(code)?.parentCode;
      if (parent) next.add(parent);
    } else {
      next.delete(code);
      if (metadata.get(code)?.permissionType === 'PAGE') {
        all.filter((item) => item.parentCode === code).forEach((item) => next.delete(item.permissionCode));
      }
    }
  }
  if (selected && ownerKey) {
    const parent = `PAGE_${ownerKey.replaceAll('-', '_').toUpperCase()}`;
    if (changes.some((code) => metadata.get(code)?.permissionType === 'BUTTON')) next.add(parent);
  }
  return [...next];
}
