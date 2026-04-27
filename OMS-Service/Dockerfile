FROM node:20.14.0-alpine

RUN apk --no-cache add \
    chromium \
    nss \
    freetype \
    harfbuzz \
    ca-certificates \
    ttf-freefont

# Install chrome-aws-lambda
RUN yarn add chrome-aws-lambda
# Create and change to the app directory.
WORKDIR /usr/src/app
ENV PORT 8080
# ENV NODE_ENV production
ENV HOST 0.0.0.0
RUN npm i -g pm2

RUN node -v

ENV OPENSSL_CONF=/dev/null

COPY package*.json yarn.lock ./

RUN yarn install
COPY . /usr/src/app

# Database update commands
# RUN npx sequelize db:migrate

CMD [ "yarn", "start" ]
EXPOSE 8080
# Run the web service on container startup.
