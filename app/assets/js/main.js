$(document).ready(function(){

  // jplist.init();

  var slider = tns({
    container: '.client-slider',
    controls: false,
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


  // //get a jPList control element
  // const element = document.getElementById('jplist-control-element');

  // //listen to the state event
  // element.addEventListener('jplist.state', function(e) {

  //   $('.dia-box:not(:has(.searchable))').hide();
  //   $('.dia-box:has(.searchable)').show();
  //   slider.goTo('first');

  // });
});