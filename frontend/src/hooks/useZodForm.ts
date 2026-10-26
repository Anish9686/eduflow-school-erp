import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import type { FieldValues, UseFormReturn } from 'react-hook-form';
import type { z } from 'zod';

export function useZodForm<TValues extends FieldValues>(schema: z.ZodType<TValues, TValues>): UseFormReturn<TValues> {
  return useForm<TValues>({
    resolver: zodResolver(schema),
  });
}
