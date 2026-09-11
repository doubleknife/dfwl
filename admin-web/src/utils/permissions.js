export function hasPermission(userPermissions, code) {
  if (!code) return true;
  return userPermissions.includes(code);
}

export function hasAnyPermission(userPermissions, codeOrCodes) {
  if (!codeOrCodes) return true;
  const codes = Array.isArray(codeOrCodes) ? codeOrCodes : [codeOrCodes];
  return codes.some((code) => hasPermission(userPermissions, code));
}
