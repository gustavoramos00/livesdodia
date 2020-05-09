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
        $('#eventos-hoje-container').replaceWith(html);
        initElements(slider);
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
  if (!('serviceWorker' in navigator)) {
    // Service Worker isn't supported on this browser, disable or hide UI.
    return;
  }
  
  if (!('PushManager' in window)) {
    // Push isn't supported on this browser, disable or hide UI.
    return;
  }

  $('.icones-evento span.icon-bell').css('display', 'inline');

  navigator.serviceWorker.register('/assets/js/service-worker.js')
  .then(function(registration) {
    console.log('Service worker successfully registered.');
    return registration;
  })
  .catch(function(err) {
    console.error('Unable to register service worker.', err);
  });
 
}

function askPermission() {
  console.log('ask permission');
  return new Promise(function(resolve, reject) {
    const permissionResult = Notification.requestPermission(function(result) {
      resolve(result);
    });

    if (permissionResult) {
      permissionResult.then(resolve, reject);
    }
  })
  .then(function(permissionResult) {
    if (permissionResult !== 'granted') {
      throw new Error('We weren\'t granted permission.');
    }
  });
}
