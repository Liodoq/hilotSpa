/** Backend base URL. Change here only. */
export const API_BASE =
  typeof location !== 'undefined' && location.port === '4200'
    ? 'http://localhost:8080/api/v1'
    : '/api/v1';