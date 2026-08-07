import React, { useEffect, useState } from 'react';
import { apiBlob } from '../api';

// Busca a mídia com o token JWT (tags <img>/<a> não enviam Authorization) e a
// serve via object URL local, resolvendo o "não abre a foto".
export function AuthImage({ url, alt }: { url: string; alt: string }) {
  const [src, setSrc] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let active = true;
    let objectUrl: string | null = null;
    apiBlob(url)
      .then((blob) => {
        if (!active) return;
        objectUrl = URL.createObjectURL(blob);
        setSrc(objectUrl);
      })
      .catch(() => active && setFailed(true));
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [url]);

  if (failed) return <div className="thumb thumb-fallback">!</div>;
  if (!src) return <div className="thumb thumb-loading" />;
  return <img src={src} alt={alt} loading="lazy" />;
}

export async function openAttachment(url: string) {
  const blob = await apiBlob(url);
  const objectUrl = URL.createObjectURL(blob);
  window.open(objectUrl, '_blank', 'noopener');
  setTimeout(() => URL.revokeObjectURL(objectUrl), 60000);
}
