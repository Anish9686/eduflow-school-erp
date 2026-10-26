import type { PropsWithChildren } from 'react';

interface FieldHintProps extends PropsWithChildren {
  id: string;
  kind?: 'help' | 'error';
}

export function FieldHint({ id, kind = 'help', children }: FieldHintProps) {
  return <p id={id} className={`field-hint field-hint--${kind}`}>{children}</p>;
}
