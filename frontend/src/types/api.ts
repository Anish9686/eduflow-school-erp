export interface ApiInfo {
  name: string;
  version: string;
  status: string;
}

export interface ApiFieldError {
  field: string;
  message: string;
}

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  fieldErrors: ApiFieldError[];
  traceId: string;
}
