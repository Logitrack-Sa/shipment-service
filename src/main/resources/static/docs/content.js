export const icons = {
  box: '<path d="m3 7 9-5 9 5v10l-9 5-9-5V7Z"/><path d="m3 7 9 5 9-5M12 12v10M7.5 4.5l9 5v5"/>',
  users: '<circle cx="9" cy="8" r="3"/><path d="M3 21v-3a6 6 0 0 1 12 0v3M16 5a3 3 0 0 1 0 6M21 21v-3a6 6 0 0 0-4-5.7"/>',
  route: '<circle cx="6" cy="5" r="3"/><circle cx="18" cy="19" r="3"/><path d="M9 5h7a4 4 0 0 1 0 8H8a4 4 0 0 0 0 8h7"/>',
  book: '<path d="M12 6c-3-3-7-3-10-1v15c3-2 7-2 10 1 3-3 7-3 10-1V5c-3-2-7-2-10 1ZM12 6v15"/>',
  terminal: '<rect x="2" y="4" width="20" height="16" rx="3"/><path d="m6 9 3 3-3 3M12 15h5"/>',
  layers: '<path d="m12 3 10 5-10 5L2 8l10-5ZM2 12l10 5 10-5M2 16l10 5 10-5"/>',
  alert: '<path d="m12 3 10 18H2L12 3ZM12 9v5M12 17v.1"/>',
  copy: '<rect x="8" y="8" width="12" height="14" rx="2"/><path d="M15 8V4a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h4"/>',
  check: '<path d="m5 12 4 4L19 6"/>'
};

export const errorCodes = [
  [401, "UNAUTHORIZED", "Se requiere un JWT válido de security-service."],
  [403, "ACCESS_DENIED", "El rol o la pertenencia no permiten esta operación."],
  [401, "COURIER_UNAUTHORIZED", "Courier rechazó la autenticación recibida."],
  [403, "COURIER_FORBIDDEN", "Courier rechazó los permisos de la operación."],
  [503, "COURIER_UNAVAILABLE", "No se pudo validar courier por caída, timeout o respuesta inválida."],
  [400, "VALIDATION_ERROR", "El cuerpo contiene campos que no cumplen las validaciones."],
  [400, "CONSTRAINT_VIOLATION", "Los parámetros de consulta no cumplen las restricciones."],
  [400, "TYPE_MISMATCH", "El UUID, estado u otro parámetro tiene un tipo inválido."],
  [400, "MALFORMED_REQUEST", "El JSON de la solicitud no se puede interpretar."],
  [404, "RESOURCE_NOT_FOUND", "No existe el cliente, repartidor o envío solicitado."],
  [409, "DUPLICATE_RESOURCE", "El recurso ya existe; por ejemplo, un correo de cliente repetido."],
  [409, "DATA_CONFLICT", "La solicitud entra en conflicto con una restricción de la base de datos."],
  [409, "CONCURRENT_MODIFICATION", "Otra operación modificó el recurso. Consulta sus datos antes de reintentar."],
  [422, "COURIER_NOT_ACTIVE", "El repartidor debe estar activo para recibir una asignación."],
  [422, "BUSINESS_RULE_VIOLATION", "La operación no está permitida por las reglas del negocio."],
  [500, "INTERNAL_SERVER_ERROR", "Ocurrió un error inesperado. La respuesta no expone detalles internos."]
];

export const sections = {
  introduction: [["resources", "Una API, todo el recorrido"], ["first-request", "Tu primera solicitud"], ["conventions", "Convenciones de la API"], ["next-steps", "Continúa integrando"]],
  quickstart: [["integration-flow", "El flujo de integración"], ["fee", "Cómo se calcula la tarifa"], ["rules", "Antes de asignar"]],
  reference: [["customers", "Clientes"], ["shipments", "Envíos"]],
  operation: [["parameters", "Parámetros"], ["request-body", "Cuerpo de la solicitud"], ["try-request", "Probar este endpoint"], ["responses", "Respuestas"]],
  models: [["model-fields", "Campos del modelo"], ["model-example", "Ejemplo JSON"]],
  errors: [["error-format", "Formato de respuesta"], ["error-codes", "Códigos y significados"], ["error-handling", "Cómo manejarlos"]]
};

export const requestDescriptions = {
  "post /api/v1/shipments": "Registra un envío para un cliente existente. El servicio calcula la tarifa, genera un tracking único y guarda el historial inicial en la misma transacción.",
  "put /api/v1/shipments/{id}/courier": "Asigna o reasigna un repartidor activo a un envío que se encuentre en estado PENDIENTE. La asignación mantiene el estado del envío.",
  "patch /api/v1/couriers/{id}/status": "Actualiza la disponibilidad operativa del repartidor. Los repartidores INACTIVOS no pueden recibir nuevas asignaciones.",
  "get /api/v1/shipments": "Consulta envíos con paginación y un filtro opcional por estado. El resultado se ordena por fecha de creación descendente y UUID."
};
