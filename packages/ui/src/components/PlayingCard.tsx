interface PlayingCardProps {
  card: string;
}

const RED_SUITS = new Set(["H", "D"]);

export function PlayingCard({ card }: PlayingCardProps) {
  const cardBackAsset = new URL("../assets/poker-card-back.svg", import.meta.url).href;
  const rank = card?.[0] ?? "?";
  const suit = card?.[1] ?? "?";
  const isHidden = card === "??" || !card || card.length < 2;
  const rankDisplay = rank === "T" ? "10" : rank;
  const suitSymbol = suit === "S" ? "♠" : suit === "H" ? "♥" : suit === "D" ? "♦" : suit === "C" ? "♣" : "?";
  const toneClass = RED_SUITS.has(suit) ? "pf-card-red" : "pf-card-black";

  return (
    <span className={`pf-card-face ${isHidden ? "pf-card-hidden" : toneClass}`}>
      {isHidden ? (
        <img className="pf-card-back-image" src={cardBackAsset} alt="暗牌" />
      ) : (
        <>
          <span className="pf-card-corner pf-card-corner-top">
            <span className="pf-card-rank">{rankDisplay}</span>
            <span className="pf-card-suit">{suitSymbol}</span>
          </span>
          <span className="pf-card-center-suit">{suitSymbol}</span>
        </>
      )}
    </span>
  );
}
