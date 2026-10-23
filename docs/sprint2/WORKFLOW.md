# Workflow del Sprint 2

Tablero: https://github.com/orgs/Logitrack-Sa/projects/2

## Inicio y reparto
30 issues nuevas: Alex 10/35 puntos; Gonzalo10/34; Damián10/35. Fibonacci estima esfuerzo, complejidad e incertidumbre, no horas ni productividad personal. Cada issue identifica responsable, criterios comprobables, contrato, forma de trabajar sin otro servicio y dependencia final.

- Alex: conservar envíos, desacoplar Courier, cliente REST, pertenencia de envíos, config/Docker shipment, regresión, CI y verificación/entrega global.
- Gonzalo: seguridad, registro sin escalación, JWT, perfil, protección courier, config/Docker security, pruebas y Postman.
- Damián: extraer repartidores, actualizar/estado/disponibilidad, Config Server y Git config, config/Docker courier, Compose y publicación.

Tres carriles empiezan juntos. Congelar campos y fixtures comunes en CONTRATOS.md permite probar con HTTP simulado, tokens locales de prueba y Git de fixture. No prometer ausencia total de dependencias: el smoke final y la publicación necesitan reunir las entregas, pero no bloquean la implementación individual. Cada responsable crea Dockerfile y pruebas de su servicio; Damián integra las imágenes.

## Issue → rama → PR → revisión → integración
1. Tomar una issue; marcar En progreso (o comentar /review status doing para sincronizar etiquetas y Project cuando PROJECT_TOKEN esté configurado).
2. Rama corta desde main actualizado: feat/s2-XX-descripcion o fix/s2-XX-descripcion. Cambios pequeños y commits breves en español.
3. Abrir PR con objetivo, evidencia real de pruebas y Closes URL_COMPLETA_DE_LA_ISSUE. En mismo repo también funciona Closes #N. En borrador sigue En progreso; listo para revisar pasa a En review.
4. CI ejecuta pruebas reproducibles del repo. IA revisa diff y criterios vinculados y publica devolución en español: hallazgos, severidad, ubicación, pruebas sugeridas y cobertura. No crea planes de implementación, no cambia asignaciones/puntos ni edita código.
5. Pedir revisión a otro integrante. Corregir y hacer push: la IA vuelve a analizar el SHA nuevo. /review en un comentario independiente solicita otra revisión del PR actual.
6. Mantenedor integra tras comprobar tests, devoluciones y revisión humana. El bot no aprueba ni fusiona PR. Un PR cerrado sin merge no completa la issue. Hecho requiere issue cerrada como completada o integración efectiva.

Preferir main como integración por repo; no crear develop por costumbre para un sprint pequeño multirrepo. Si mantienen develop, usarla conscientemente y recordar que GitHub cierra automáticamente las issues al integrar a la rama por defecto. No cerrar una issue solo porque el PR fue aceptado a una rama intermedia.

## Automatizaciones y límites
Revisor adaptado de IA_GIT_AUTOMATIZADO: solo devolución en PR, sin PM ni /plan. Copilot CLI fijado, sin herramientas, sin ejecutar archivos del PR ni recibir PROJECT_TOKEN. Contexto: diff acotado, requisitos de issues y CI disponible para el SHA. Las sugerencias de tests no equivalen a pruebas ejecutadas.

Scripts privilegiados siempre del main del repositorio base. CI de código propuesto usa pull_request y token read-only. Revisión usa pull_request_target solo con scripts de main y participantes confiables; nunca checkout ni ejecución del head. Si el modelo falla, status AI / review=error y etiqueta ai:unavailable. Diffs truncados son revisión parcial, nunca aprobación completa. Un humano revisa todo hallazgo.

GitHub Actions token se intenta con copilot-requests:write según políticas de la organización. Si Copilot no está habilitado para la organización, configurar COPILOT_GITHUB_TOKEN con permiso Copilot Requests y acceso válido. No asumir que el acceso del repo personal se transfiere a la organización. No copiar tokens entre repositorios ni publicarlos en issues.

PROJECT_NUMBER=2 y PROJECT_OWNER=Logitrack-Sa. Sin PROJECT_TOKEN, las etiquetas de estado se actualizan pero no se afirma que las tarjetas se hayan movido: el workflow informa configuración pendiente. GITHUB_TOKEN no accede al Project de organización. Configurar un GitHub App/token limitado y autorizado para ese Project, preferentemente secreto de organización restringido a estos repositorios. No reutilizar el token general de gh como secreto de Actions.

Automatizaciones nativas del Project cierran/reabren tarjetas según el estado real de la issue. Revisar los filtros de auto-add por repositorio y label sprint:2 para no mezclar el Sprint1. No hay fecha de vencimiento inventada ni merge automático. La disponibilidad real de Copilot/Projects se demuestra por una ejecución, no por la presencia del YAML.

## DoD
Criterios de la issue comprobados; pruebas locales/CI pertinentes; contrato/OpenAPI actualizado si cambió; sin secretos ni consultas cruzadas a bases; revisión humana; PR integrado. Para entrega global: Docker Hub con cuatro imágenes versionadas, Compose image-only y evidencia del flujo completo con JWT/roles/pertenencia.

Referencias oficiales: https://docs.github.com/en/copilot/how-tos/copilot-cli/automate-copilot-cli/automate-with-actions y https://docs.github.com/en/issues/planning-and-tracking-with-projects/automating-your-project/automating-projects-using-actions
