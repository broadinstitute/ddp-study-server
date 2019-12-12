FROM nginx:1.14.2

RUN \
  apt-get update \
  && apt-get install -y curl \
  && apt-get install -y nano \
  && apt-get install -y git


RUN apt-get install -qy git libpcre3 libpcre3-dev libz-dev  apache2-dev wget libxml2-dev lua5.1 lua5.1-dev && \
    cd /root && \
    git clone https://github.com/nbs-system/naxsi.git && \
    curl -fSL http://nginx.org/download/nginx-1.14.2.tar.gz -o nginx.tar.gz && \
    tar -xvzf nginx.tar.gz && \
    cd nginx-1.14.2 && \
     ./configure --with-compat --add-dynamic-module=../naxsi/naxsi_src && \
    make modules && \
    cp objs/ngx_http_naxsi_module.so /etc/nginx/modules

RUN cp -rfp /root/naxsi/naxsi_config/naxsi_core.rules /etc/nginx/modules/

RUN mkdir /app
WORKDIR /app

COPY index.html /usr/share/nginx/html/index.html
COPY ddp.png /usr/share/nginx/html/ddp.png
COPY nginx.conf /etc/nginx/nginx.conf

# Keep track of git hash
#ARG GIT_SHA
#RUN echo $GIT_SHA > git_sha.txt
