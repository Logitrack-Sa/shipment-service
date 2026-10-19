window.ui = SwaggerUIBundle({
  url: "/v3/api-docs",
  dom_id: "#swagger-ui",
  deepLinking: true,
  filter: true,
  docExpansion: "list",
  validatorUrl: null,
  presets: [SwaggerUIBundle.presets.apis]
});
