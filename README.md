# AutoCorrida

App Android leve (Kotlin puro, sem bibliotecas) que fica por cima do app de corrida e:

1. Na tela **"Ainda buscando solicitações"**, arrasta pra baixo para atualizar, no intervalo configurado.
2. Quando aparecem **cards**, para de arrastar, lê o **R$/km** de cada um e toca em **Escolher** no melhor que passar no filtro.
3. Depois de escolher, **aguarda** até a tela de busca voltar.

## Comportamento "humano"
- Arrastar começa em um ponto diferente a cada vez (22–80% da largura), com diagonal, curva e velocidade aleatórias.
- Intervalo com variação (%), e às vezes uma "pausa distraída" mais longa.
- Antes de tocar em Escolher espera um "tempo de reação" aleatório.
- O toque cai num ponto aleatório do botão, com micro-movimento e duração variável.
- Os gestos nunca tocam na barrinha flutuante.

## Barrinha flutuante
| Botão | Função |
|---|---|
| ▶ / ■ | inicia / para. A cor mostra o estado: cinza parado, verde buscando, amarelo ofertas, azul aguardando, roxo outra tela |
| ⚙ | abre as configurações |
| ✥ | segure e arraste para mover |

## Compilar pelo GitHub (sem Android Studio)
1. Crie um repositório (pode ser privado) e envie todos estes arquivos, **incluindo a pasta `.github`**.
2. A aba **Actions** compila sozinha a cada envio (ou clique em *Run workflow*).
3. Ao terminar, abra a execução e baixe **AutoCorrida-apk** em *Artifacts* (vem em .zip, o APK está dentro).

## Primeiro uso no celular
1. Instale o APK (permita "instalar apps desconhecidos").
2. Abra o AutoCorrida → botão 1 → ative **AutoCorrida** em Acessibilidade.
   - Android 13+: se a opção aparecer cinza, vá em Configurações → Apps → AutoCorrida → ⋮ → **Permitir configurações restritas**, e tente de novo.
3. Botão 2 → libere da otimização de bateria.
4. Ajuste os valores e salve. Abra o app de corrida e toque ▶ na barrinha.
5. Dica: com o serviço ativo, a tela de config mostra o "último app lido". Copie esse nome para o campo **Pacote do app de corrida** para o robô só agir nele.

## Se não detectar a tela
Alguns apps escondem os textos da acessibilidade. Confira se os textos "Ainda buscando" e "Escolher" batem com o que aparece na tela (campos em *Textos da tela*). Se mesmo assim nada for lido, será preciso a versão com leitura por imagem (OCR).

> Aviso: automação viola os termos de uso da maioria dos apps de corrida e pode levar a bloqueio da conta. Use por sua conta e risco.
