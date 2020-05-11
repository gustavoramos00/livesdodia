#!/bin/bash

tag=${1:-"master"}
host=${2:-"empty"}
app_dir=/var/play

if [ "$tag" = "master" ]
then
  echo "Erro: TAG não informada. Informar a versão, ex '1.6.1'."
  exit 1
fi

if [ "$host" = "empty" ]
then
  echo "Erro: HOST não informado."
  exit 1
fi


echo "Compilando projeto"
rm -rf target/universal/*zip
sbt dist

echo "Enviando artefato"
scp target/universal/livesdodia-$tag.zip $host:$app_dir/livesdodia-$tag.zip

echo "Atualizando na pasta destino"
ssh $host /bin/bash <<EOF
  rm -rf $app_dir/livesdodia-$tag
  unzip -q -o $app_dir/livesdodia-$tag.zip -d $app_dir/
  unzip -q -o $app_dir/livesdodia-$tag/lib/br.com.livesdodia.livesdodia-$tag-assets.jar 'public/*' -d $app_dir/livesdodia-assets
  rm $app_dir/livesdodia-$tag.zip
  ln -sfn $app_dir/livesdodia-$tag $app_dir/livesdodia
  sudo systemctl restart livesdodia@1
EOF
