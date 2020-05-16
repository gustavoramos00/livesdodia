
var vapidPublicKey = 'BPenScjfnRdAhNcPNLP92IYxCgyz_nVnFf2CP3XrvgyG419tWqHua5SM0WGxoZXpliBhd0mrZd9lH0N7K0YPdOk';
var liveSubscribeId;
var liveSubscribedNome;
var livesSubscrebedIds;
$(document).ready(function(){

  initScroll();
  initNotification();
  jplist.init();

  //get a jPList control element
  const element = document.getElementById('jplist-control-element');

  //listen to the state event
  element.addEventListener('jplist.state', function(e) {

    /** 
     * Oculta dias que não tem eventos satisfazendo os critérios de busca 
    */
    $('.dia-box:not(:has(.searchable))').hide();
    $('.dia-box:has(.searchable)').show();
    /**
     * Exibe 'Busca não encontrada' nas seções
     */
    $('.search-not-found-container:not(:has(.searchable))').find('.busca-nao-encontrada').show();
    $('.search-not-found-container:has(.searchable)').find('.busca-nao-encontrada').hide();

  });
    

  initElements();

  /******************************
  * Atualiza seção Eventos do Dia
  ******************************/
  setInterval(function(){
    $.ajax({
      url: '/eventoshoje',
      dataType: 'html',
      success: function(html) {
        $('#eventos-hoje').replaceWith(html);
        initElements();
        btnLivesAtivas();
      }
    });
  },90000); // atualiza a cada 1min30s

});

function initElements() {

  $( "a[ga-share-control]" ).click(function() {
    var control = $(this).attr('ga-share-control');
    var evento = $(this).attr('ga-share-data');
    ga('send', {
      hitType: 'event',
      eventCategory: 'Share',
      eventAction: control,
      eventLabel: evento
    });
  });

  $( "a[ga-link-control]" ).click(function() {
    var control = $(this).attr('ga-link-control');
    var evento = $(this).attr('ga-share-data');
    ga('send', {
      hitType: 'event',
      eventCategory: 'Link',
      eventAction: control,
      eventLabel: evento
    });
  });
  
  //refresh jPList
  jplist.resetContent();

}

function initScroll() {
    /***
   * Show and Hide header container
   */
  var prevScrollpos = window.pageYOffset;
  window.onscroll = function() {
    var currentScrollPos = window.pageYOffset;
    var containerTop = $(".yt-player");
    var containerBottom = $('.tags');
    
    if (prevScrollpos > currentScrollPos) {
      containerTop.css('top', 0);
      containerBottom.css('bottom', -containerBottom.height());
    } else {
      if (player) {
        containerTop.css('top', -(containerTop.height()*0.6));
      }
      containerBottom.css('bottom', 0);
    }
    

    prevScrollpos = currentScrollPos;
  }
}

function initNotification() {
  setTimeout(function(){
    var hasSW = 'serviceWorker' in navigator;
    var hasPM = 'PushManager' in window;
    if (hasSW && hasPM) {
  
      $('.notification-toggle button.notification-button').show("slow", function() {
        $('.notification-toggle').removeClass("notification-toggle");
      });

      navigator.serviceWorker.register(serviceWorkerPath)
        .then(function(registration) {
          fetchSubscribedLives();
          return registration;
        })
        .catch(function(err) {
          ga('send', {
            hitType: 'event',
            eventCategory: 'Push Error',
            eventAction: 'Push - Erro registrar sw',
            eventLabel: 'Push - Erro registrar sw'
          });
        });
    } else {
      $('button.notification-button').remove();
    }
  },350);
 
}

function fetchSubscribedLives() {
  if (Notification.permission == "granted") {
    navigator.serviceWorker.getRegistration('/assets/js/')
    .then(function(sw) {
      return sw.pushManager.getSubscription();
    })
    .then(function(sub) {
      if (sub) {
        $.ajax({
          url: '/fetch-subscribed-lives?id=' + sub.toJSON().keys.p256dh ,
          dataType: 'json',
          success: function(livesId) {
            btnLivesAtivas(livesId);
          }
        });
      }
    });
  }
}

function btnLivesAtivas(livesId) {
  if (livesId) {
    livesSubscrebedIds = livesId;
  }
  $('button[btn-push-live-id]').removeClass('btn-active-push');
  if (livesSubscrebedIds && livesSubscrebedIds.length > 0) {
    for (var i = 0; i < livesSubscrebedIds.length; i++) {
      var liveId = livesSubscrebedIds[i];
      $('button[btn-push-live-id="' + liveId + '"]').addClass('btn-active-push');
    }
  }
}

function subscribeLive(id, nome) {
  if (id) {
    liveSubscribeId = id;
  }
  if (nome) {
    liveSubscribedNome = nome;
  }
  if (Notification.permission == "denied") {
    $('#pushModalDenied').modal();
    ga('send', {
      hitType: 'event',
      eventCategory: 'Push',
      eventAction: 'Push - Permissão negada',
      eventLabel: 'Push - Permissão negada'
    });
  } else if (id && Notification.permission !== "granted") {
    $('#pushModal').modal();
    ga('send', {
      hitType: 'event',
      eventCategory: 'Push',
      eventAction: 'Push - Solicitar permissão',
      eventLabel: 'Push - Solicitar permissão'
    });
  } else {
    return navigator.serviceWorker.getRegistration('/assets/js/')
    .then(function(registration) {
      const subscribeOptions = {
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(vapidPublicKey)
      };

      return registration.pushManager.subscribe(subscribeOptions);
    })
    .then(function(pushSubscription) {
      ga('send', {
        hitType: 'event',
        eventCategory: 'Push',
        eventAction: 'Push Live',
        eventLabel: 'Push Live - ' + liveSubscribedNome
      });
      return sendSubscriptionToBackEnd(pushSubscription, liveSubscribeId);
    })
    .catch(function(error) {
      ga('send', {
        hitType: 'event',
        eventCategory: 'Push Error',
        eventAction: 'Push - Erro subscribe',
        eventLabel: 'Push - Erro subscribe'
      });
    });  
  }
}

function sendSubscriptionToBackEnd(subscription, id) {
  return $.ajax('/subscribe-live?id=' + id, {
    method: 'POST',
    contentType: 'application/json',
    data: JSON.stringify(subscription),
    success: function(livesId) {
      btnLivesAtivas(livesId);
    },
    error: function(response) {
      ga('send', {
        hitType: 'event',
        eventCategory: 'Push Error',
        eventAction: 'Push - Erro send subscription',
        eventLabel: 'Push - Erro send subscription'
      });
      throw new Error('Bad status code from server.');
    }
  });
}


/**
 * https://github.com/GoogleChromeLabs/web-push-codelab/blob/master/app/scripts/main.js
 */
function urlBase64ToUint8Array(base64String) {
  const padding = '='.repeat((4 - base64String.length % 4) % 4);
  const base64 = (base64String + padding)
    .replace(/\-/g, '+')
    .replace(/_/g, '/');

  const rawData = window.atob(base64);
  const outputArray = new Uint8Array(rawData.length);

  for (i = 0; i < rawData.length; ++i) {
    outputArray[i] = rawData.charCodeAt(i);
  }
  return outputArray;
}
