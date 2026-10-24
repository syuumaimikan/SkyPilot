/** Direct private-network targets only. Credentials and RustDesk configuration URIs are not supported. */
export function rustDeskTarget(input: string): string | null {
  const parts = input.trim().toLowerCase().split(":");
  if (parts.length > 2) return null;
  const host = parts[0] ?? "";
  const dns = host.length <= 253 && host.endsWith(".ts.net") && host.slice(0, -7).includes(".")
    && /^[a-z0-9]+(?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9]+(?:[a-z0-9-]*[a-z0-9])?)+$/.test(host);
  const octets = host.split(".");
  const ip = octets.length === 4 && octets.every((part) => /^(0|[1-9][0-9]{0,2})$/.test(part) && Number(part) <= 255)
    && Number(octets[0]) === 100 && Number(octets[1]) >= 64 && Number(octets[1]) <= 127;
  const port = parts.length === 2 ? parts[1] : "21118";
  if ((!dns && !ip) || !port || !/^[1-9][0-9]{0,4}$/.test(port) || Number(port) > 65535) return null;
  return `${host}:${port}`;
}

export function rustDeskLink(input: string): string | null {
  const target = rustDeskTarget(input);
  return target ? `rustdesk://connection/new/${target}` : null;
}
