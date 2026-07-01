FROM node:20-alpine
WORKDIR /app

# Copia os arquivos de configuração primeiro para aproveitar o cache do Docker
COPY package*.json ./

# Instala as dependências (node_modules)
RUN npm install

# Copia todo o resto do seu código Vue para o contêiner
COPY . .

# Expõe a porta que definimos para o front-end
EXPOSE 5173

# Comando para rodar o servidor de desenvolvimento do Vue.
# O parâmetro --host 0.0.0.0 é obrigatório no Docker para liberar o acesso externo.
ENTRYPOINT ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "5173"]