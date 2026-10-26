import { z } from 'zod';

export const requiredTextSchema = z.string().trim().min(1, 'This field is required');

export const optionalTextSchema = z.string().trim().optional();
