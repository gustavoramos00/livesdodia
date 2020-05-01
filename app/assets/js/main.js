$(document).ready(function(){

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


  jplist.init();


  $('.testi-meta-inner').on('click touch', function () {
    var control = '#' + $(this).attr('collapse-control');
    $(this).attr('aria-expanded',true);
    $(control).collapse('toggle');
  });

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
});