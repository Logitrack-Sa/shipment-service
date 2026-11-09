# Seguimiento público

`GET /api/v1/shipments/public-tracking/{code}` permite consultar un envío sin JWT.
El código tiene el formato `LT-` seguido de diez letras mayúsculas o números. Los
códigos se siguen generando con `SecureRandom`; no cambian los envíos existentes.

La respuesta contiene exclusivamente `trackingCode`, `status`, `createdAt` y
`movements` (`status`, `changedAt`). No incluye UUID, cliente, repartidor,
direcciones, teléfono, precio ni observaciones libres del historial. No se
devuelve fecha estimada porque el dominio todavía no la calcula.

La consulta no modifica datos y responde con `Cache-Control: no-store`. Un código
mal formado devuelve 400 y uno inexistente, 404. Los endpoints de detalle,
seguimiento privado, historial completo y escritura conservan su autorización.

El Gateway permite únicamente el GET público y aplica un presupuesto compartido
de 120 consultas por minuto por instancia, configurable con
`logitrack.tracking.requests-per-minute`. Devuelve 429 y `Retry-After: 60` al
agotarse. No es una cuota por usuario/IP ni una cuota distribuida: al escalar el
Gateway debe sustituirse por un limitador compartido. El backend se mantiene
en la red privada y las consultas externas entran por el Gateway.

La web `/seguimiento?codigo=LT-...` usa esta vista pública y no redirige al detalle
privado. El código compartido permite conocer el estado del paquete; tratá ese
código como un dato que se entrega al remitente y destinatario.
