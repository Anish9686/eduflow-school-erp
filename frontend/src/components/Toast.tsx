import { createContext, useContext, useState } from 'react';
import type { PropsWithChildren } from 'react';
import type { Tone } from './DesignSystem';

type ToastMessage = { id: number; message: string; tone: Tone };
const ToastContext = createContext<{ showToast: (message: string, tone?: Tone) => void } | null>(null);

export function ToastProvider({ children }: PropsWithChildren) {
  const [items, setItems] = useState<ToastMessage[]>([]);
  function showToast(message: string, tone: Tone = 'success') {
    const id = Date.now() + Math.random();
    setItems(previous => [...previous, { id, message, tone }]);
    window.setTimeout(() => setItems(previous => previous.filter(item => item.id !== id)), 4000);
  }
  return <ToastContext.Provider value={{ showToast }}>{children}<div className="ds-toasts" aria-live="polite" aria-atomic="false">{items.map(item => <div key={item.id} className={`ds-toast ds-toast--${item.tone}`} role="status">{item.message}</div>)}</div></ToastContext.Provider>;
}

export function useToast() {
  const value = useContext(ToastContext);
  if (!value) throw new Error('useToast must be used within ToastProvider');
  return value;
}
