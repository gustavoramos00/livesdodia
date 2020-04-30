### Instalar aplicação Play ###


Instalar serviço, copiando o conteudo do arquivo na pasta **conf/systemd**:

    $ sudo vi /etc/systemd/system/livesdodia@.service
    
Habilitar e iniciar o serviço

     $ sudo systemctl daemon-reload  
     $ sudo systemctl enable livesdodia@{0,1}.service
     $ sudo systemctl start livesdodia@{0,1}.service
     $ sudo systemctl status livesdodia@{0,1}.service

### Certificado LetsEncrypt

Ver aqui: https://certbot.eff.org/lets-encrypt/ubuntubionic-nginx

     $ sudo certbot certonly --nginx

### Configura Nginx

### Configura Timezone


### Configura Locale

### Localmente subir REDIS com DOCKER

    $ docker run -p 6379:6379 redis:3.0.6