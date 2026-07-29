FROM node:22-alpine

WORKDIR /app
COPY --chown=node:node mock-systems.mjs ./mock-systems.mjs

ENV PORT=8081
EXPOSE 8081

USER node
CMD ["node", "/app/mock-systems.mjs"]
