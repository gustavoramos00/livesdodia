
var vapidPublicKey = 'BPenScjfnRdAhNcPNLP92IYxCgyz_nVnFf2CP3XrvgyG419tWqHua5SM0WGxoZXpliBhd0mrZd9lH0N7K0YPdOk';
var liveSubscribeId;
$(document).ready(function(){

  initScroll();
  initNotification();
  jplist.init();

  var slider = tns({
    container: '.client-slider',
    controls: false,
    arrowKeys: true,
    items: 1,
    mouseDrag: true,
    loop: false,
    responsive: {
      768: {
        items: 2
      },
      992: {
        items: 3
      }
    }
  });

  //get a jPList control element
  const element = document.getElementById('jplist-control-element');

  //listen to the state event
  element.addEventListener('jplist.state', function(e) {

    /** 
     * Oculta dias que não tem eventos satisfazendo os critérios de busca 
    */
    $('.dia-box:not(:has(.searchable))').hide();
    $('.dia-box:has(.searchable)').show();
    slider.goTo('first');

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
    var beta = document.location.href.includes("/beta");
    var hasSW = 'serviceWorker' in navigator;
    var hasPM = 'PushManager' in window;
    if (beta && hasSW && hasPM) {
  
      $('.notification-toggle button.notification-button').show("slow", function() {
        $('.notification-toggle').removeClass("notification-toggle");
      });

      navigator.serviceWorker.register(serviceWorkerPath)
        .then(function(registration) {
          fetchSubscribedLives();
          return registration;
        })
        .catch(function(err) {
          console.error('Unable to register service worker.', err);
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
          url: '/fetch-subscribed-lives/' + sub.toJSON().keys.p256dh ,
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
  $('button[btn-push-live-id]').removeClass('btn-active-push');
  if (livesId && livesId.length > 0) {
    for (var i = 0; i < livesId.length; i++) {
      var liveId = livesId[i];
      $('button[btn-push-live-id=' + liveId + ']').addClass('btn-active-push');
    }
  }
}

function subscribeLive(id) {
  if (id) {
    liveSubscribeId = id;
  }
  if (Notification.permission == "denied") {
    $('#pushModalDenied').modal();
  } else if (id && Notification.permission !== "granted") {
    $('#pushModal').modal();
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
      return sendSubscriptionToBackEnd(pushSubscription, liveSubscribeId);
    })
    .catch(function(error) {
      // TODO tratar não aceitação das notificações
      console.log('Erro', error);
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
      console.log('error response', response)
      throw new Error('Bad status code from server.');
    }
  });
  // .then(function(response) {
  //   if (!response.ok) {
  //     throw new Error('Bad status code from server.');
  //   }
  //   return response.json();
  // })
  // .then(function(responseData) {
  //   if (!(responseData.data && responseData.data.success)) {
  //     throw new Error('Bad response from server.');
  //   }
  // });
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
